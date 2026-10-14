package com.msspoker.shopservice.service;

import com.msspoker.shopservice.config.PaymentProperties;
import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.dto.response.OrderResponse;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ItemSource;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.event.VipCoinTopupSuccessEvent;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.mapper.OrderMapper;
import com.msspoker.shopservice.producer.TopupEventProducer;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import com.msspoker.shopservice.repository.OrderRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.impl.OrderServiceImpl;
import com.msspoker.shopservice.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.exception.InvalidSignatureException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;
import vn.payos.service.blocking.webhooks.WebhooksService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CoinBundleRepository coinBundleRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private TopupEventProducer topupEventProducer;

    @Spy
    private OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    @Mock
    private PayOS payOS;

    @Mock
    private PaymentRequestsService paymentRequestsService;

    @Mock
    private WebhooksService webhooksService;

    @Mock
    private com.msspoker.shopservice.service.PaymentConfigService paymentConfigService;

    @Mock
    private com.msspoker.shopservice.client.WalletClient walletClient;

    @Mock
    private com.msspoker.shopservice.service.PaymentIdempotencyService paymentIdempotencyService;

    private OrderValidator orderValidator;

    private OrderServiceImpl orderService;

    private UUID accountId;
    private Product product;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        orderValidator = new OrderValidator(inventoryItemRepository);

        Category category = Category.builder()
                .id(UUID.randomUUID())
                .code("CARD_BACK")
                .name("Card Backs")
                .build();

        product = Product.builder()
                .id(UUID.randomUUID())
                .category(category)
                .name("Royal Card Back")
                .description("Luxury cards")
                .tier(ProductTier.VIP)
                .priceVnd(50000L)
                .assetUrl("https://assets.msspoker.com/card1.png")
                .isExclusive(false)
                .isDeleted(false)
                .build();

        pendingOrder = Order.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .product(product)
                .amountVnd(50000L)
                .payosOrderCode(100001L)
                .checkoutUrl("https://payos.vn/checkout/100001")
                .qrCode("vietqr-string-code")
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        com.msspoker.shopservice.entity.PaymentConfig defaultConfig = com.msspoker.shopservice.entity.PaymentConfig.builder()
                .provider(com.msspoker.shopservice.enums.PaymentProvider.PAYOS)
                .name("Cổng PayOS Mặc Định")
                .active(true)
                .main(true)
                .build();
        org.mockito.Mockito.lenient().when(paymentConfigService.getActiveEntity()).thenReturn(defaultConfig);
        org.mockito.Mockito.lenient().when(paymentIdempotencyService.acquireLock(any(), any(), any())).thenReturn(true);

        PaymentProperties paymentProperties = PaymentProperties.builder()
                .returnUrl("http://localhost:5173/shop/payment/success")
                .cancelUrl("http://localhost:5173/shop/payment/cancel")
                .expireMinutes(15)
                .vietqrTemplateUrl("https://img.vietqr.io/image")
                .build();

        orderService = new OrderServiceImpl(
                orderRepository,
                productRepository,
                coinBundleRepository,
                inventoryItemRepository,
                orderMapper,
                orderValidator,
                payOS,
                topupEventProducer,
                paymentConfigService,
                walletClient,
                paymentProperties,
                paymentIdempotencyService
        );
    }

    @Test
    @DisplayName("createOrder - Thành công: tạo link PayOS và lưu Order trạng thái PENDING")
    void createOrder_Success() {
        CreateOrderRequest request = new CreateOrderRequest(product.getId());

        when(productRepository.findByIdAndIsDeletedFalse(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.generatePayosOrderCode()).thenReturn(100001L);
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse payosResponse = mock(CreatePaymentLinkResponse.class);
        when(payosResponse.getCheckoutUrl()).thenReturn("https://payos.vn/checkout/100001");
        when(payosResponse.getQrCode()).thenReturn("vietqr-sample-qr");

        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payosResponse);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(accountId, request);

        assertThat(response).isNotNull();
        assertThat(response.amountVnd()).isEqualTo(50000L);
        assertThat(response.payosOrderCode()).isEqualTo(100001L);
        assertThat(response.checkoutUrl()).isEqualTo("https://payos.vn/checkout/100001");
        assertThat(response.qrCode()).isEqualTo("vietqr-sample-qr");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order captured = orderCaptor.getValue();
        assertThat(captured.getAccountId()).isEqualTo(accountId);
        assertThat(captured.getProduct()).isEqualTo(product);
        assertThat(captured.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("createOrder - Thất bại: Không tìm thấy sản phẩm")
    void createOrder_ProductNotFound() {
        UUID unknownProductId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(unknownProductId);

        when(productRepository.findByIdAndIsDeletedFalse(unknownProductId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(accountId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SHOP_PRODUCT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("createOrder - Thất bại: Lỗi cổng PayOS ném SHOP_PAYOS_SERVICE_ERROR")
    void createOrder_PayosError() {
        CreateOrderRequest request = new CreateOrderRequest(product.getId());

        when(productRepository.findByIdAndIsDeletedFalse(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.generatePayosOrderCode()).thenReturn(100001L);
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class)))
                .thenThrow(new RuntimeException("PayOS connection timeout"));

        assertThatThrownBy(() -> orderService.createOrder(accountId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SHOP_PAYOS_SERVICE_ERROR")
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("handlePayosWebhook - Thành công: cập nhật đơn hàng PAID và cấp phát vật phẩm ACID")
    void handlePayosWebhook_Success() {
        WebhookData webhookData = mock(WebhookData.class);
        when(webhookData.getOrderCode()).thenReturn(100001L);
        when(webhookData.getAmount()).thenReturn(50000L);

        Webhook webhook = mock(Webhook.class);
        when(webhook.getCode()).thenReturn("00");

        when(payOS.webhooks()).thenReturn(webhooksService);
        when(webhooksService.verify(webhook)).thenReturn(webhookData);
        when(orderRepository.findByPayosOrderCodeForUpdate(100001L)).thenReturn(Optional.of(pendingOrder));
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, product.getId())).thenReturn(false);

        orderService.handlePayosWebhook(webhook);

        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(pendingOrder.getPaidAt()).isNotNull();

        verify(orderRepository).save(pendingOrder);

        ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository).save(itemCaptor.capture());
        InventoryItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getAccountId()).isEqualTo(accountId);
        assertThat(savedItem.getProduct()).isEqualTo(product);
        assertThat(savedItem.getSource()).isEqualTo(ItemSource.PURCHASE);
        assertThat(savedItem.getGrantKey()).isEqualTo("ORDER:" + pendingOrder.getId());
    }

    @Test
    @DisplayName("handlePayosWebhook - Idempotency: Đơn hàng đã PAID thì bỏ qua và không cấp đồ lần 2")
    void handlePayosWebhook_Idempotency() {
        pendingOrder.setStatus(OrderStatus.PAID);

        WebhookData webhookData = mock(WebhookData.class);
        when(webhookData.getOrderCode()).thenReturn(100001L);

        Webhook webhook = mock(Webhook.class);

        when(payOS.webhooks()).thenReturn(webhooksService);
        when(webhooksService.verify(webhook)).thenReturn(webhookData);
        when(orderRepository.findByPayosOrderCodeForUpdate(100001L)).thenReturn(Optional.of(pendingOrder));

        orderService.handlePayosWebhook(webhook);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("handlePayosWebhook - Chữ ký không hợp lệ ném SHOP_PAYOS_INVALID_SIGNATURE")
    void handlePayosWebhook_InvalidSignature() {
        Webhook webhook = mock(Webhook.class);

        when(payOS.webhooks()).thenReturn(webhooksService);
        when(webhooksService.verify(webhook)).thenThrow(new InvalidSignatureException("Signature mismatch"));

        assertThatThrownBy(() -> orderService.handlePayosWebhook(webhook))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SHOP_PAYOS_INVALID_SIGNATURE")
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("cancelOrder - Thành công: Hủy đơn PENDING")
    void cancelOrder_Success() {
        when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(accountId, pendingOrder.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(paymentRequestsService).cancel(eq(100001L), eq("User cancelled"));
    }

    @Test
    @DisplayName("cancelOrder - Thất bại: Đơn hàng không ở trạng thái PENDING ném SHOP_ORDER_NOT_PENDING")
    void cancelOrder_NotPending() {
        pendingOrder.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(accountId, pendingOrder.getId()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SHOP_ORDER_NOT_PENDING")
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("createBundleOrder - Thành công: Tạo đơn nạp VIP Coin qua PayOS")
    void createBundleOrder_Success() {
        UUID bundleId = UUID.randomUUID();
        CoinBundle bundle = CoinBundle.builder()
                .id(bundleId)
                .code("BUNDLE_100K")
                .name("Gói Đặc Biệt 100K")
                .priceVnd(100000L)
                .baseVipCoin(100000L)
                .bonusVipCoin(15000L)
                .bonusPercentage(15)
                .badgeLabel("POPULAR")
                .isActive(true)
                .sortOrder(3)
                .build();

        when(coinBundleRepository.findByIdAndIsActiveTrue(bundleId)).thenReturn(Optional.of(bundle));
        when(orderRepository.generatePayosOrderCode()).thenReturn(200002L);
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse payosResp = mock(CreatePaymentLinkResponse.class);
        when(payosResp.getCheckoutUrl()).thenReturn("https://payos.vn/checkout/200002");
        when(payosResp.getQrCode()).thenReturn("vietqr-bundle-code");
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payosResp);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createBundleOrder(accountId, bundleId);

        assertThat(response).isNotNull();
        assertThat(response.bundleId()).isEqualTo(bundleId);
        assertThat(response.bundleName()).isEqualTo("Gói Đặc Biệt 100K");
        assertThat(response.vipCoinAmount()).isEqualTo(115000L);
        assertThat(response.amountVnd()).isEqualTo(100000L);
        assertThat(response.payosOrderCode()).isEqualTo(200002L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.qrImageUrl()).contains("200002");
    }

    @Test
    @DisplayName("createBundleOrder - Thất bại: Gói không tồn tại ném SHOP_BUNDLE_NOT_FOUND")
    void createBundleOrder_BundleNotFound() {
        UUID bundleId = UUID.randomUUID();
        when(coinBundleRepository.findByIdAndIsActiveTrue(bundleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createBundleOrder(accountId, bundleId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SHOP_BUNDLE_NOT_FOUND")
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("handlePayosWebhook - Gói Nạp Thành công: Gửi event VipCoinTopupSuccessEvent lên RabbitMQ")
    void handlePayosWebhook_BundleSuccess_PublishesTopupEvent() {
        UUID bundleId = UUID.randomUUID();
        CoinBundle bundle = CoinBundle.builder()
                .id(bundleId)
                .code("BUNDLE_50K")
                .name("Gói 50K")
                .priceVnd(50000L)
                .baseVipCoin(50000L)
                .bonusVipCoin(5000L)
                .bonusPercentage(10)
                .isActive(true)
                .sortOrder(2)
                .build();

        Order bundleOrder = Order.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .bundle(bundle)
                .vipCoinAmount(55000L)
                .amountVnd(50000L)
                .payosOrderCode(300003L)
                .status(OrderStatus.PENDING)
                .build();

        WebhookData webhookData = mock(WebhookData.class);
        when(webhookData.getOrderCode()).thenReturn(300003L);
        when(webhookData.getAmount()).thenReturn(50000L);

        Webhook webhook = mock(Webhook.class);
        when(webhook.getCode()).thenReturn("00");

        when(payOS.webhooks()).thenReturn(webhooksService);
        when(webhooksService.verify(webhook)).thenReturn(webhookData);
        when(orderRepository.findByPayosOrderCodeForUpdate(300003L)).thenReturn(Optional.of(bundleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.handlePayosWebhook(webhook);

        assertThat(bundleOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(bundleOrder.getPaidAt()).isNotNull();

        ArgumentCaptor<VipCoinTopupSuccessEvent> eventCaptor = ArgumentCaptor.forClass(VipCoinTopupSuccessEvent.class);
        verify(topupEventProducer).sendTopupSuccessEvent(eventCaptor.capture());

        VipCoinTopupSuccessEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.orderId()).isEqualTo(bundleOrder.getId());
        assertThat(publishedEvent.accountId()).isEqualTo(accountId);
        assertThat(publishedEvent.bundleId()).isEqualTo(bundleId);
        assertThat(publishedEvent.vipCoinAmount()).isEqualTo(55000L);
        assertThat(publishedEvent.payosOrderCode()).isEqualTo(300003L);
        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder - Idempotency: Người dùng click đúp, trả về đơn PENDING cũ còn hạn mà không gọi PayOS lần 2")
    void createOrder_Idempotency_ReturnsExistingPendingOrder() {
        CreateOrderRequest request = new CreateOrderRequest(product.getId());

        when(productRepository.findByIdAndIsDeletedFalse(product.getId())).thenReturn(Optional.of(product));

        Order pendingOrder = Order.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .product(product)
                .amountVnd(50000L)
                .payosOrderCode(100099L)
                .checkoutUrl("https://payos.vn/checkout/100099")
                .qrCode("existing-qr")
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(orderRepository.findFirstByAccountIdAndProductIdAndStatusOrderByCreatedAtDesc(
                accountId, product.getId(), OrderStatus.PENDING))
                .thenReturn(Optional.of(pendingOrder));

        OrderResponse response = orderService.createOrder(accountId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(pendingOrder.getId());
        assertThat(response.payosOrderCode()).isEqualTo(100099L);
        assertThat(response.checkoutUrl()).isEqualTo("https://payos.vn/checkout/100099");
        assertThat(response.qrCode()).isEqualTo("existing-qr");

        // Không gọi sinh mã PayOS mới hay lưu đơn hàng mới
        verify(payOS, never()).paymentRequests();
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder - Đơn PENDING cũ đã hết hạn: Hủy đơn cũ và tạo đơn mới")
    void createOrder_StalePendingOrder_CancelsAndCreatesNew() {
        CreateOrderRequest request = new CreateOrderRequest(product.getId());

        when(productRepository.findByIdAndIsDeletedFalse(product.getId())).thenReturn(Optional.of(product));

        Order expiredPendingOrder = Order.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .product(product)
                .amountVnd(50000L)
                .payosOrderCode(100088L)
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(60)) // đã hết hạn
                .build();

        when(orderRepository.findFirstByAccountIdAndProductIdAndStatusOrderByCreatedAtDesc(
                accountId, product.getId(), OrderStatus.PENDING))
                .thenReturn(Optional.of(expiredPendingOrder));

        when(orderRepository.generatePayosOrderCode()).thenReturn(100089L);
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse payosResponse = mock(CreatePaymentLinkResponse.class);
        when(payosResponse.getCheckoutUrl()).thenReturn("https://payos.vn/checkout/100089");
        when(payosResponse.getQrCode()).thenReturn("new-qr");
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payosResponse);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(accountId, request);

        assertThat(response).isNotNull();
        assertThat(response.payosOrderCode()).isEqualTo(100089L);
        assertThat(expiredPendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("createBundleOrder - Idempotency: Người dùng click đúp gói nạp, trả về đơn PENDING cũ")
    void createBundleOrder_Idempotency_ReturnsExistingPendingOrder() {
        UUID bundleId = UUID.randomUUID();
        CoinBundle bundle = CoinBundle.builder()
                .id(bundleId)
                .code("BUNDLE_50K")
                .name("Gói 50K")
                .priceVnd(50000L)
                .baseVipCoin(50000L)
                .bonusVipCoin(5000L)
                .isActive(true)
                .build();

        when(coinBundleRepository.findByIdAndIsActiveTrue(bundleId)).thenReturn(Optional.of(bundle));

        Order existingBundleOrder = Order.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .bundle(bundle)
                .vipCoinAmount(55000L)
                .amountVnd(50000L)
                .payosOrderCode(200055L)
                .checkoutUrl("https://payos.vn/checkout/200055")
                .qrCode("bundle-qr")
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(orderRepository.findFirstByAccountIdAndBundleIdAndStatusOrderByCreatedAtDesc(
                accountId, bundleId, OrderStatus.PENDING))
                .thenReturn(Optional.of(existingBundleOrder));

        OrderResponse response = orderService.createBundleOrder(accountId, bundleId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existingBundleOrder.getId());
        assertThat(response.payosOrderCode()).isEqualTo(200055L);
        verify(payOS, never()).paymentRequests();
    }

    @Test
    @DisplayName("createOrder - Redis Idempotency HIT: Trả về kết quả từ Redis cache < 1ms mà không chạm DB/PayOS")
    void createOrder_RedisIdempotency_ReturnsCachedOrderImmediately() {
        CreateOrderRequest request = new CreateOrderRequest(product.getId());

        OrderResponse cachedResponse = new OrderResponse(
                UUID.randomUUID(),
                accountId,
                product.getId(),
                product.getName(),
                null,
                null,
                null,
                50000L,
                999001L,
                "https://payos.vn/checkout/999001",
                "cached-qr",
                "cached-qr-img",
                OrderStatus.PENDING,
                null,
                Instant.now().plusSeconds(600),
                Instant.now()
        );

        when(productRepository.findByIdAndIsDeletedFalse(product.getId())).thenReturn(Optional.of(product));
        when(paymentIdempotencyService.getCachedOrder(accountId, product.getId())).thenReturn(Optional.of(cachedResponse));

        OrderResponse result = orderService.createOrder(accountId, request);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(cachedResponse.id());
        assertThat(result.payosOrderCode()).isEqualTo(999001L);
        assertThat(result.qrCode()).isEqualTo("cached-qr");

        // Xác minh không gọi DB check, không gọi PayOS và không lưu đơn mới
        verify(orderRepository, never()).findFirstByAccountIdAndProductIdAndStatusOrderByCreatedAtDesc(any(), any(), any());
        verify(payOS, never()).paymentRequests();
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBundleOrder - Redis Idempotency HIT: Trả về kết quả gói nạp từ Redis cache")
    void createBundleOrder_RedisIdempotency_ReturnsCachedOrderImmediately() {
        UUID bundleId = UUID.randomUUID();
        CoinBundle bundle = CoinBundle.builder()
                .id(bundleId)
                .code("BUNDLE_100K")
                .name("Gói 100K")
                .priceVnd(100000L)
                .baseVipCoin(100000L)
                .bonusVipCoin(15000L)
                .isActive(true)
                .build();

        OrderResponse cachedResponse = new OrderResponse(
                UUID.randomUUID(),
                accountId,
                null,
                null,
                bundleId,
                "Gói 100K",
                115000L,
                100000L,
                888001L,
                "https://payos.vn/checkout/888001",
                "cached-bundle-qr",
                "cached-bundle-qr-img",
                OrderStatus.PENDING,
                null,
                Instant.now().plusSeconds(600),
                Instant.now()
        );

        when(coinBundleRepository.findByIdAndIsActiveTrue(bundleId)).thenReturn(Optional.of(bundle));
        when(paymentIdempotencyService.getCachedOrder(accountId, bundleId)).thenReturn(Optional.of(cachedResponse));

        OrderResponse result = orderService.createBundleOrder(accountId, bundleId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(cachedResponse.id());
        assertThat(result.payosOrderCode()).isEqualTo(888001L);

        verify(orderRepository, never()).findFirstByAccountIdAndBundleIdAndStatusOrderByCreatedAtDesc(any(), any(), any());
        verify(payOS, never()).paymentRequests();
        verify(orderRepository, never()).save(any());
    }
}
