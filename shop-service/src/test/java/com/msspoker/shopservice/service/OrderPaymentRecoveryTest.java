package com.msspoker.shopservice.service;

import com.msspoker.shopservice.client.WalletClient;
import com.msspoker.shopservice.config.PaymentProperties;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.enums.OrderFulfillmentStatus;
import com.msspoker.shopservice.enums.OrderStatus;
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
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;
import vn.payos.service.blocking.webhooks.WebhooksService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPaymentRecoveryTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CoinBundleRepository bundles = mock(CoinBundleRepository.class);
    private final InventoryItemRepository inventory = mock(InventoryItemRepository.class);
    private final PayOS payOS = mock(PayOS.class);
    private final PaymentRequestsService payments = mock(PaymentRequestsService.class);
    private final WebhooksService webhooks = mock(WebhooksService.class);
    private final WalletClient wallet = mock(WalletClient.class);
    private final TopupEventProducer events = mock(TopupEventProducer.class);
    private final PaymentIdempotencyService cache = mock(PaymentIdempotencyService.class);
    private final UUID account = UUID.randomUUID();
    private Order order;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        var bundle = CoinBundle.builder().id(UUID.randomUUID()).code("TEST").name("Fixture")
                .priceVnd(50000).baseVipCoin(50000).bonusVipCoin(5000).isActive(true).build();
        order = Order.builder().id(UUID.randomUUID()).accountId(account).bundle(bundle).vipCoinAmount(55000L)
                .amountVnd(50000).payosOrderCode(100001).status(OrderStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(10)).build();
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orders.findByPayosOrderCodeForUpdate(order.getPayosOrderCode())).thenReturn(Optional.of(order));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payOS.paymentRequests()).thenReturn(payments);
        when(payOS.webhooks()).thenReturn(webhooks);
        service = new OrderServiceImpl(orders, mock(ProductRepository.class), bundles, inventory,
                Mappers.getMapper(OrderMapper.class), mock(OrderValidator.class), payOS, events,
                mock(PaymentConfigService.class), wallet, PaymentProperties.builder().expireMinutes(5).build(), cache);
    }

    private Webhook successfulWebhook() {
        var webhook = mock(Webhook.class);
        var data = mock(WebhookData.class);
        when(webhook.getCode()).thenReturn("00");
        when(data.getOrderCode()).thenReturn(order.getPayosOrderCode());
        when(data.getAmount()).thenReturn(order.getAmountVnd());
        when(webhooks.verify(webhook)).thenReturn(data);
        return webhook;
    }

    private PaymentLink link(PaymentLinkStatus status) {
        PaymentLink reply = new PaymentLink();
        reply.setOrderCode(order.getPayosOrderCode());
        reply.setAmount(order.getAmountVnd());
        reply.setAmountPaid(status == PaymentLinkStatus.PAID ? order.getAmountVnd() : 0L);
        reply.setStatus(status);
        return reply;
    }

    @Test
    void bothDeliveryPathsFailButPaidOrderIsRetriedWithTheSameIdempotencyKey() {
        doThrow(new IllegalStateException("wallet offline")).doNothing().when(wallet)
                .creditVipCoin(account, 55000, "TOPUP_BUNDLE_PAYOS", "100001", "ORDER:" + order.getId());
        doThrow(new IllegalStateException("broker offline")).when(events).sendTopupSuccessEvent(any());
        service.handlePayosWebhook(successfulWebhook());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.PENDING);
        assertThat(order.getFulfilledAt()).isNull();
        Instant paidAt = order.getPaidAt();
        service.reconcileOrder(order.getId());
        assertThat(order.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.FULFILLED);
        assertThat(order.getFulfilledAt()).isNotNull();
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        verify(wallet, times(2)).creditVipCoin(account, 55000, "TOPUP_BUNDLE_PAYOS", "100001", "ORDER:" + order.getId());
        verify(payments, never()).get(anyLong());
    }

    @Test
    void queuedMessageAloneDoesNotClaimThatWalletHasCreditedTheOrder() {
        doThrow(new IllegalStateException("wallet offline")).when(wallet).creditVipCoin(any(), anyLong(), any(), any(), any());
        service.handlePayosWebhook(successfulWebhook());
        verify(events).sendTopupSuccessEvent(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.PENDING);
    }

    @Test
    void duplicateCompletedOrLegacyPaidOrdersAreNotAutomaticallyCredited() {
        order.setStatus(OrderStatus.PAID);
        for (var status : List.of(OrderFulfillmentStatus.FULFILLED, OrderFulfillmentStatus.REVIEW_REQUIRED)) {
            order.setFulfillmentStatus(status);
            service.handlePayosWebhook(successfulWebhook());
            service.reconcileOrder(order.getId());
        }
        verifyNoInteractions(wallet, events);
    }

    @Test
    void payosTimeoutDoesNotExpireAnOverdueOrderAndItRemainsDiscoverable() {
        when(payments.get(order.getPayosOrderCode())).thenThrow(new IllegalStateException("timeout"));
        when(orders.findByAccountIdAndStatusOrderByPayosOrderCodeAsc(account, OrderStatus.PENDING)).thenReturn(List.of(order));
        service.reconcileOrder(order.getId());
        assertThat(service.getActivePendingOrder(account).id()).isEqualTo(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(payments, never()).cancel(anyLong(), anyString());
        verifyNoInteractions(wallet);
    }

    @Test
    void failedExpiryCancellationKeepsTheOrderPending() {
        when(payments.get(order.getPayosOrderCode())).thenReturn(link(PaymentLinkStatus.PENDING));
        when(payments.cancel(order.getPayosOrderCode(), "Expired")).thenThrow(new IllegalStateException("timeout"));
        service.reconcileOrder(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void confirmedExpiryCancellationClosesTheOrder() {
        when(payments.get(order.getPayosOrderCode())).thenReturn(link(PaymentLinkStatus.PENDING));
        when(payments.cancel(order.getPayosOrderCode(), "Expired")).thenReturn(link(PaymentLinkStatus.CANCELLED));
        service.reconcileOrder(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verifyNoInteractions(wallet);
    }

    @Test
    void paymentWinningTheExpiryCancellationRaceIsFulfilledInsteadOfExpired() {
        when(payments.get(order.getPayosOrderCode())).thenReturn(link(PaymentLinkStatus.PENDING));
        when(payments.cancel(order.getPayosOrderCode(), "Expired")).thenReturn(link(PaymentLinkStatus.PAID));
        service.reconcileOrder(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.FULFILLED);
    }

    @Test
    void processingPaymentIsNotCancelledJustBecauseTheLocalDeadlinePassed() {
        when(payments.get(order.getPayosOrderCode())).thenReturn(link(PaymentLinkStatus.PROCESSING));
        service.reconcileOrder(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(payments, never()).cancel(anyLong(), anyString());
    }

    @Test
    void userCancelTimeoutDoesNotClaimTheOrderWasCancelled() {
        when(payments.cancel(order.getPayosOrderCode(), "User cancelled")).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> service.cancelOrder(account, order.getId())).isInstanceOf(AppException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void userCancellationObservingAPaidOrderReturnsTheConfirmedPaidState() {
        when(payments.cancel(order.getPayosOrderCode(), "User cancelled")).thenReturn(link(PaymentLinkStatus.PAID));
        var result = service.cancelOrder(account, order.getId());
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.fulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.FULFILLED);
    }

    @Test
    void invalidPaymentReferenceOrAmountCannotTriggerFulfillment() {
        PaymentLink wrongCode = link(PaymentLinkStatus.PAID);
        wrongCode.setOrderCode(987654L);
        PaymentLink wrongAmount = link(PaymentLinkStatus.PAID);
        wrongAmount.setAmount(10000L);
        PaymentLink underpaid = link(PaymentLinkStatus.PAID);
        underpaid.setAmountPaid(1L);
        for (var bad : List.of(wrongCode, wrongAmount, underpaid)) {
            when(payments.get(order.getPayosOrderCode())).thenReturn(bad);
            assertThatThrownBy(() -> service.reconcileOrder(order.getId())).isInstanceOf(AppException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
        verifyNoInteractions(wallet, events);
    }

    @Test
    void paidBundleWithoutThePurchasedAmountSnapshotRequiresReview() {
        order.setVipCoinAmount(null);
        service.handlePayosWebhook(successfulWebhook());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.REVIEW_REQUIRED);
        verifyNoInteractions(wallet, events);
    }
}
