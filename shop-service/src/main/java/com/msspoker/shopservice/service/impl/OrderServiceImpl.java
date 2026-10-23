package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.client.WalletClient;
import com.msspoker.shopservice.config.PaymentProperties;
import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.dto.response.OrderResponse;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.entity.PaymentConfig;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ItemSource;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.enums.PaymentProvider;
import com.msspoker.shopservice.event.VipCoinTopupSuccessEvent;
import com.msspoker.shopservice.exception.OrderExceptions;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.mapper.OrderMapper;
import com.msspoker.shopservice.producer.TopupEventProducer;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import com.msspoker.shopservice.repository.OrderRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.OrderService;
import com.msspoker.shopservice.service.PaymentConfigService;
import com.msspoker.shopservice.service.PaymentIdempotencyService;
import com.msspoker.shopservice.validator.OrderValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.exception.InvalidSignatureException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CoinBundleRepository coinBundleRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final OrderMapper orderMapper;
    private final OrderValidator orderValidator;
    private final PayOS payOS;
    private final TopupEventProducer topupEventProducer;
    private final PaymentConfigService paymentConfigService;
    private final WalletClient walletClient;
    private final PaymentProperties paymentProperties;
    private final PaymentIdempotencyService paymentIdempotencyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(UUID accountId, CreateOrderRequest request) {
        log.info("Initiating order creation for accountId={}, productId={}", accountId, request.productId());

        Product product = productRepository.findByIdAndIsDeletedFalse(request.productId())
                .orElseThrow(ProductExceptions::productNotFound);

        // Redis Idempotency Check
        Optional<OrderResponse> cachedOrder = paymentIdempotencyService.getCachedOrder(accountId, product.getId());
        if (cachedOrder.isPresent()) {
            return cachedOrder.get();
        }

        // Database Idempotency Check
        Optional<Order> existingOrderOpt = orderRepository.findFirstByAccountIdAndProductIdAndStatusOrderByCreatedAtDesc(
                accountId, product.getId(), OrderStatus.PENDING);
        if (existingOrderOpt.isPresent()) {
            Order pendingOrder = existingOrderOpt.get();
            if (pendingOrder.getExpiresAt() != null && pendingOrder.getExpiresAt().isAfter(Instant.now())) {
                log.info("Idempotent createOrder detected for accountId={}, productId={}. Returning existing order id={}, orderCode={}",
                        accountId, product.getId(), pendingOrder.getId(), pendingOrder.getPayosOrderCode());
                OrderResponse response = orderMapper.toOrderResponse(pendingOrder);
                Duration remainingTtl = Duration.between(Instant.now(), pendingOrder.getExpiresAt());
                if (!remainingTtl.isNegative() && !remainingTtl.isZero()) {
                    paymentIdempotencyService.cacheOrder(accountId, product.getId(), response, remainingTtl);
                }
                return response;
            } else {
                log.info("Expiring stale pending order id={} for accountId={}", pendingOrder.getId(), accountId);
                pendingOrder.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(pendingOrder);
                paymentIdempotencyService.evictOrder(accountId, product.getId());
            }
        }

        // Khóa phân tán Redis chống race condition
        boolean lockAcquired = paymentIdempotencyService.acquireLock(accountId, product.getId(), Duration.ofSeconds(10));
        if (!lockAcquired) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Optional<OrderResponse> polledResponse = paymentIdempotencyService.getCachedOrder(accountId, product.getId());
            if (polledResponse.isPresent()) {
                return polledResponse.get();
            }
        }

        try {
            // Run validation qua OrderValidator
            orderValidator.validateCreateOrder(accountId, request, product);

            // Generate atomic unique sequence-based PayOS order code
            Long orderCode = orderRepository.generatePayosOrderCode();
            if (orderCode == null) {
                orderCode = System.currentTimeMillis();
            }

            Instant now = Instant.now();
            Instant expiresAt = now.plus(Duration.ofMinutes(paymentProperties.getExpireMinutes()));

            // Short alphanumeric description for PayOS (must not exceed 25 chars)
            String description = "MSS " + orderCode;

            PaymentConfig activeConfig = paymentConfigService.getActiveEntity();
            String checkoutUrl;
            String qrCode;

            if (activeConfig.getProvider() == PaymentProvider.MANUAL_BANK && activeConfig.getBankCode() != null && activeConfig.getAccountNumber() != null) {
                String encodedDesc = URLEncoder.encode(description, StandardCharsets.UTF_8);
                String qrUrl = String.format("%s/%s-%s-compact2.png?amount=%d&addInfo=%s",
                        paymentProperties.getVietqrTemplateUrl(), activeConfig.getBankCode(), activeConfig.getAccountNumber(), product.getPriceVnd(), encodedDesc);
                checkoutUrl = qrUrl;
                qrCode = qrUrl;
                log.info("Tạo mã VietQR chuyển khoản trực tiếp cho orderCode={}: {}", orderCode, qrUrl);
            } else {
                PaymentLinkItem item = PaymentLinkItem.builder()
                        .name(product.getName())
                        .quantity(1)
                        .price(product.getPriceVnd())
                        .build();

                CreatePaymentLinkRequest payosRequest = CreatePaymentLinkRequest.builder()
                        .orderCode(orderCode)
                        .amount(product.getPriceVnd())
                        .description(description)
                        .returnUrl(paymentProperties.getReturnUrl())
                        .cancelUrl(paymentProperties.getCancelUrl())
                        .items(List.of(item))
                        .expiredAt(expiresAt.getEpochSecond())
                        .build();

                try {
                    log.info("Requesting PayOS payment link for orderCode={}, amount={}", orderCode, product.getPriceVnd());
                    CreatePaymentLinkResponse payosResponse = payOS.paymentRequests().create(payosRequest);
                    checkoutUrl = payosResponse.getCheckoutUrl();
                    qrCode = payosResponse.getQrCode();
                } catch (Exception ex) {
                    log.error("Failed to create PayOS payment link for orderCode={}: {}", orderCode, ex.getMessage(), ex);
                    throw OrderExceptions.payosServiceError(ex.getMessage());
                }
            }

            Order order = Order.builder()
                    .accountId(accountId)
                    .product(product)
                    .amountVnd(product.getPriceVnd())
                    .payosOrderCode(orderCode)
                    .checkoutUrl(checkoutUrl)
                    .qrCode(qrCode)
                    .status(OrderStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            Order savedOrder = orderRepository.save(order);
            log.info("Order created successfully with id={}, payosOrderCode={}, expiresAt={}",
                    savedOrder.getId(), savedOrder.getPayosOrderCode(), expiresAt);

            OrderResponse response = orderMapper.toOrderResponse(savedOrder);
            paymentIdempotencyService.cacheOrder(accountId, product.getId(), response,
                    Duration.ofMinutes(paymentProperties.getExpireMinutes()));

            return response;
        } finally {
            if (lockAcquired) {
                paymentIdempotencyService.releaseLock(accountId, product.getId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createBundleOrder(UUID accountId, UUID bundleId) {
        log.info("Initiating bundle order creation for accountId={}, bundleId={}", accountId, bundleId);

        CoinBundle bundle = coinBundleRepository.findByIdAndIsActiveTrue(bundleId)
                .orElseThrow(OrderExceptions::bundleNotFound);

        // 1. Redis Idempotency Check
        Optional<OrderResponse> cachedOrder = paymentIdempotencyService.getCachedOrder(accountId, bundleId);
        if (cachedOrder.isPresent()) {
            return cachedOrder.get();
        }

        // 2. Database & Active Pending Order Check
        Optional<Order> activePendingOpt = orderRepository.findFirstByAccountIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                accountId, OrderStatus.PENDING, Instant.now());
        if (activePendingOpt.isPresent()) {
            Order pendingOrder = activePendingOpt.get();
            // Sync with PayOS to see if user has already paid the previous order
            syncOrderEntity(pendingOrder);
            if (pendingOrder.getStatus() == OrderStatus.PAID) {
                log.info("Previous pending order id={}, orderCode={} was already paid. Proceeding.",
                        pendingOrder.getId(), pendingOrder.getPayosOrderCode());
            } else if (pendingOrder.getBundle() != null && pendingOrder.getBundle().getId().equals(bundleId)) {
                log.info("Idempotent createBundleOrder detected for accountId={}, bundleId={}. Returning existing bundle order id={}, orderCode={}",
                        accountId, bundleId, pendingOrder.getId(), pendingOrder.getPayosOrderCode());
                OrderResponse response = orderMapper.toOrderResponse(pendingOrder);
                Duration remainingTtl = Duration.between(Instant.now(), pendingOrder.getExpiresAt());
                if (!remainingTtl.isNegative() && !remainingTtl.isZero()) {
                    paymentIdempotencyService.cacheOrder(accountId, bundleId, response, remainingTtl);
                }
                return response;
            } else {
                // User switched bundles; cancel old pending order so user does not have dangling orders
                log.info("User switched bundle or created new order. Cancelling previous pending order id={}, orderCode={}",
                        pendingOrder.getId(), pendingOrder.getPayosOrderCode());
                try {
                    payOS.paymentRequests().cancel(pendingOrder.getPayosOrderCode(), "User switched to a different bundle");
                } catch (Exception ex) {
                    log.warn("Could not cancel previous order on PayOS gateway: {}", ex.getMessage());
                }
                pendingOrder.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(pendingOrder);
                paymentIdempotencyService.evictOrder(accountId,
                        pendingOrder.getBundle() != null ? pendingOrder.getBundle().getId() : (pendingOrder.getProduct() != null ? pendingOrder.getProduct().getId() : bundleId));
            }
        }


        // 3. Khóa phân tán Redis chống race condition
        boolean lockAcquired = paymentIdempotencyService.acquireLock(accountId, bundleId, Duration.ofSeconds(10));
        if (!lockAcquired) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Optional<OrderResponse> polledResponse = paymentIdempotencyService.getCachedOrder(accountId, bundleId);
            if (polledResponse.isPresent()) {
                return polledResponse.get();
            }
        }

        try {
            Long orderCode = orderRepository.generatePayosOrderCode();
            if (orderCode == null) {
                orderCode = System.currentTimeMillis();
            }

            Instant now = Instant.now();
            Instant expiresAt = now.plus(Duration.ofMinutes(paymentProperties.getExpireMinutes()));

            String description = "MSS VIP " + orderCode;
            if (description.length() > 25) {
                description = description.substring(0, 25);
            }

            PaymentConfig activeConfig = paymentConfigService.getActiveEntity();
            String checkoutUrl;
            String qrCode;

            if (activeConfig.getProvider() == PaymentProvider.MANUAL_BANK && activeConfig.getBankCode() != null && activeConfig.getAccountNumber() != null) {
                String encodedDesc = URLEncoder.encode(description, StandardCharsets.UTF_8);
                String qrUrl = String.format("%s/%s-%s-compact2.png?amount=%d&addInfo=%s",
                        paymentProperties.getVietqrTemplateUrl(), activeConfig.getBankCode(), activeConfig.getAccountNumber(), bundle.getPriceVnd(), encodedDesc);
                checkoutUrl = qrUrl;
                qrCode = qrUrl;
                log.info("Tạo mã VietQR chuyển khoản trực tiếp cho bundle orderCode={}: {}", orderCode, qrUrl);
            } else {
                PaymentLinkItem item = PaymentLinkItem.builder()
                        .name(bundle.getName())
                        .quantity(1)
                        .price(bundle.getPriceVnd())
                        .build();

                CreatePaymentLinkRequest payosRequest = CreatePaymentLinkRequest.builder()
                        .orderCode(orderCode)
                        .amount(bundle.getPriceVnd())
                        .description(description)
                        .returnUrl(paymentProperties.getReturnUrl())
                        .cancelUrl(paymentProperties.getCancelUrl())
                        .items(List.of(item))
                        .expiredAt(expiresAt.getEpochSecond())
                        .build();

                try {
                    log.info("Requesting PayOS payment link for bundle orderCode={}, amount={}", orderCode, bundle.getPriceVnd());
                    CreatePaymentLinkResponse payosResponse = payOS.paymentRequests().create(payosRequest);
                    checkoutUrl = payosResponse.getCheckoutUrl();
                    qrCode = payosResponse.getQrCode();
                } catch (Exception ex) {
                    log.error("Failed to create PayOS payment link for bundle orderCode={}: {}", orderCode, ex.getMessage(), ex);
                    throw OrderExceptions.payosServiceError(ex.getMessage());
                }
            }

            Order order = Order.builder()
                    .accountId(accountId)
                    .bundle(bundle)
                    .vipCoinAmount(bundle.getVipCoinAmount())
                    .amountVnd(bundle.getPriceVnd())
                    .payosOrderCode(orderCode)
                    .checkoutUrl(checkoutUrl)
                    .qrCode(qrCode)
                    .status(OrderStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            Order savedOrder = orderRepository.save(order);
            log.info("Bundle order created successfully with id={}, payosOrderCode={}, vipCoinAmount={}",
                    savedOrder.getId(), savedOrder.getPayosOrderCode(), savedOrder.getVipCoinAmount());

            OrderResponse response = orderMapper.toOrderResponse(savedOrder);
            paymentIdempotencyService.cacheOrder(accountId, bundleId, response,
                    Duration.ofMinutes(paymentProperties.getExpireMinutes()));

            return response;
        } finally {
            if (lockAcquired) {
                paymentIdempotencyService.releaseLock(accountId, bundleId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse getOrderDetail(UUID accountId, UUID orderId) {
        log.debug("Fetching order detail for accountId={}, orderId={}", accountId, orderId);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getAccountId().equals(accountId))
                .orElseThrow(OrderExceptions::orderNotFound);

        if (order.getStatus() == OrderStatus.PENDING) {
            syncOrderEntity(order);
        }

        return orderMapper.toOrderResponse(order);
    }

    @Override
    public List<OrderResponse> getMyOrders(UUID accountId) {
        log.debug("Fetching orders list for accountId={}", accountId);
        List<Order> orders = orderRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        return orderMapper.toOrderResponseList(orders);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancelOrder(UUID accountId, UUID orderId) {
        log.info("Cancelling order orderId={} by accountId={}", orderId, accountId);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getAccountId().equals(accountId))
                .orElseThrow(OrderExceptions::orderNotFound);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw OrderExceptions.orderNotPending();
        }

        try {
            payOS.paymentRequests().cancel(order.getPayosOrderCode(), "User cancelled");
        } catch (Exception ex) {
            log.warn("Could not cancel order on PayOS gateway for orderCode={}: {}",
                    order.getPayosOrderCode(), ex.getMessage());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        evictOrderCache(savedOrder);
        log.info("Order id={} successfully marked as CANCELLED", orderId);
        return orderMapper.toOrderResponse(savedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePayosWebhook(Webhook webhook) {
        log.info("Received PayOS webhook with code={}, desc={}", webhook.getCode(), webhook.getDesc());


        WebhookData data;
        try {
            data = payOS.webhooks().verify(webhook);
        } catch (InvalidSignatureException ex) {
            log.error("Invalid PayOS webhook signature detected: {}", ex.getMessage());
            throw OrderExceptions.invalidWebhookSignature();
        } catch (Exception ex) {
            log.error("PayOS webhook verification failed: {}", ex.getMessage(), ex);
            throw OrderExceptions.invalidWebhookSignature();
        }

        Long orderCode = data.getOrderCode();
        log.info("Webhook verified successfully for orderCode={}, amount={}", orderCode, data.getAmount());

        // 2. Acquire Pessimistic Write Lock (SELECT ... FOR UPDATE) to prevent race conditions & deadlocks
        Order order = orderRepository.findByPayosOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> {
                    log.error("Order not found for PayOS orderCode={}", orderCode);
                    return OrderExceptions.orderNotFound();
                });

        // 3. Idempotency Check: if already paid, acknowledge 200 without duplicate side effects
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Order code={} is already marked as PAID. Acknowledging webhook idempotently.", orderCode);
            return;
        }

        // 4. Handle successful payment code ("00")
        if ("00".equals(webhook.getCode())) {
            // Verify payment amount
            if (data.getAmount() < order.getAmountVnd()) {
                log.error("Amount mismatch for orderCode={}: expected={}, received={}",
                        orderCode, order.getAmountVnd(), data.getAmount());
                return;
            }

            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(Instant.now());
            orderRepository.save(order);
            evictOrderCache(order);

            fulfillOrder(order);
        } else {
            log.warn("Payment webhook received non-success code={} for orderCode={}", webhook.getCode(), orderCode);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse syncOrder(UUID accountId, UUID orderId) {
        log.info("Chủ động đồng bộ trạng thái đơn hàng từ PayOS: orderId={}, accountId={}", orderId, accountId);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getAccountId().equals(accountId))
                .orElseThrow(OrderExceptions::orderNotFound);

        if (order.getStatus() == OrderStatus.PENDING) {
            syncOrderEntity(order);
        }

        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse getActivePendingOrder(UUID accountId) {
        log.debug("Checking active pending order for accountId={}", accountId);
        Instant now = Instant.now();
        Optional<Order> pendingOrderOpt = orderRepository.findFirstByAccountIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                accountId, OrderStatus.PENDING, now);

        if (pendingOrderOpt.isEmpty()) {
            return null;
        }

        Order order = pendingOrderOpt.get();
        syncOrderEntity(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            return null;
        }

        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcileOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        syncOrderEntity(order);
    }

    private void syncOrderEntity(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        try {
            PaymentLink link = payOS.paymentRequests().get(order.getPayosOrderCode());
            PaymentLinkStatus status = link.getStatus();
            log.info("Kiểm tra trạng thái từ PayOS: orderCode={}, status={}", order.getPayosOrderCode(), status);

            if (PaymentLinkStatus.PAID.equals(status)) {
                log.info("Đơn hàng code={} đã thanh toán thành công (PAID). Cập nhật và hoàn tất đơn hàng.", order.getPayosOrderCode());
                order.setStatus(OrderStatus.PAID);
                order.setPaidAt(Instant.now());
                orderRepository.save(order);
                evictOrderCache(order);
                fulfillOrder(order);
            } else if (PaymentLinkStatus.CANCELLED.equals(status)) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
                evictOrderCache(order);
            } else if (PaymentLinkStatus.EXPIRED.equals(status) || (order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now()))) {
                log.info("Đơn hàng code={} đã hết hạn. Đánh dấu EXPIRED.", order.getPayosOrderCode());
                order.setStatus(OrderStatus.EXPIRED);
                orderRepository.save(order);
                evictOrderCache(order);
                if (PaymentLinkStatus.PENDING.equals(status)) {
                    try {
                        payOS.paymentRequests().cancel(order.getPayosOrderCode(), "Expired");
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Không thể truy vấn PayOS cho orderCode={}: {}", order.getPayosOrderCode(), ex.getMessage());
            if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now())) {
                order.setStatus(OrderStatus.EXPIRED);
                orderRepository.save(order);
                evictOrderCache(order);
            }
        }
    }

    private void evictOrderCache(Order order) {
        UUID targetId = order.getProduct() != null ? order.getProduct().getId() :
                (order.getBundle() != null ? order.getBundle().getId() : null);
        if (targetId != null) {
            paymentIdempotencyService.evictOrder(order.getAccountId(), targetId);
        }
    }

    private void fulfillOrder(Order order) {
        if (order.getBundle() != null) {
            long vipCoinAmount = order.getVipCoinAmount() != null
                    ? order.getVipCoinAmount()
                    : order.getBundle().getVipCoinAmount();

            VipCoinTopupSuccessEvent event = VipCoinTopupSuccessEvent.builder()
                    .orderId(order.getId())
                    .accountId(order.getAccountId())
                    .bundleId(order.getBundle().getId())
                    .vipCoinAmount(vipCoinAmount)
                    .payosOrderCode(order.getPayosOrderCode())
                    .source("PAYOS_BUNDLE")
                    .timestamp(Instant.now())
                    .build();

            try {
                topupEventProducer.sendTopupSuccessEvent(event);
                log.info("Đã phát sự kiện nạp VIP Coin cho bundle orderId={}, accountId={}, vipCoin={}",
                        order.getId(), order.getAccountId(), vipCoinAmount);
            } catch (Exception ex) {
                log.warn("Không thể gửi event RabbitMQ: {}. Kích hoạt HTTP fallback sang wallet-service.", ex.getMessage());
                try {
                    walletClient.creditVipCoin(
                            order.getAccountId(),
                            vipCoinAmount,
                            "TOPUP_BUNDLE_PAYOS",
                            String.valueOf(order.getPayosOrderCode()),
                            "ORDER:" + order.getId()
                    );
                    log.info("HTTP Fallback: Đã cộng {} VIP Coin thành công cho accountId={}", vipCoinAmount, order.getAccountId());
                } catch (Exception httpEx) {
                    log.error("HTTP Fallback sang wallet-service cũng thất bại: {}", httpEx.getMessage(), httpEx);
                }
            }
        } else if (order.getProduct() != null) {
            String grantKey = "ORDER:" + order.getId();
            boolean alreadyOwned = inventoryItemRepository.existsByAccountIdAndProductId(
                    order.getAccountId(), order.getProduct().getId()
            );

            if (!alreadyOwned) {
                InventoryItem item = InventoryItem.builder()
                        .accountId(order.getAccountId())
                        .product(order.getProduct())
                        .source(ItemSource.PURCHASE)
                        .isEquipped(false)
                        .grantKey(grantKey)
                        .build();
                inventoryItemRepository.save(item);
                log.info("Đã cấp vật phẩm productId={} cho accountId={} từ orderId={}",
                        order.getProduct().getId(), order.getAccountId(), order.getId());
            } else {
                log.warn("Tài khoản {} đã sở hữu sản phẩm {} khi hoàn tất đơn hàng {}. Bỏ qua cấp trùng.",
                        order.getAccountId(), order.getProduct().getId(), order.getId());
            }
        }
    }
}
