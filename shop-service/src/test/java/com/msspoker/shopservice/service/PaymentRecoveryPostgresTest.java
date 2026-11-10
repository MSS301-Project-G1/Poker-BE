package com.msspoker.shopservice.service;

import com.msspoker.shopservice.client.WalletClient;
import com.msspoker.shopservice.config.PaymentProperties;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.enums.OrderFulfillmentStatus;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.mapper.OrderMapper;
import com.msspoker.shopservice.producer.TopupEventProducer;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.repository.OrderRepository;
import com.msspoker.shopservice.service.impl.OrderServiceImpl;
import com.msspoker.shopservice.validator.OrderValidator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;
import vn.payos.service.blocking.webhooks.WebhooksService;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = PaymentRecoveryPostgresTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.config.import=", "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "spring.cache.type=none", "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
                "spring.rabbitmq.listener.simple.auto-startup=false"})
@DirtiesContext
class PaymentRecoveryPostgresTest {
    private static final AtomicLong CODES = new AtomicLong(800000);
    @Autowired private OrderService service;
    @Autowired private OrderRepository orders;
    @Autowired private CoinBundleRepository bundles;
    @Autowired private WalletClient wallet;
    @Autowired private TopupEventProducer events;
    @Autowired private PayOS payOS;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void setUp() {
        reset(wallet, events, payOS);
    }

    private Order order(OrderStatus status) {
        CoinBundle bundle = bundles.saveAndFlush(CoinBundle.builder().code("RECOVERY_" + UUID.randomUUID())
                .name("Recovery fixture").priceVnd(50000).baseVipCoin(50000).bonusVipCoin(5000).isActive(true).build());
        return orders.saveAndFlush(Order.builder().accountId(UUID.randomUUID()).bundle(bundle).vipCoinAmount(55000L)
                .amountVnd(50000).payosOrderCode(CODES.incrementAndGet()).status(status)
                .paidAt(status == OrderStatus.PAID ? Instant.now() : null)
                .expiresAt(Instant.now().minusSeconds(60)).build());
    }

    private Webhook webhook(Order order) {
        var api = mock(WebhooksService.class);
        var webhook = mock(Webhook.class);
        var data = mock(WebhookData.class);
        when(payOS.webhooks()).thenReturn(api);
        when(api.verify(webhook)).thenReturn(data);
        when(webhook.getCode()).thenReturn("00");
        when(data.getOrderCode()).thenReturn(order.getPayosOrderCode());
        when(data.getAmount()).thenReturn(order.getAmountVnd());
        return webhook;
    }

    @Test
    void paidStateAndOutstandingFulfillmentSurviveTheFailedDeliveryTransaction() {
        var order = order(OrderStatus.PENDING);
        doThrow(new IllegalStateException("wallet offline")).doNothing().when(wallet)
                .creditVipCoin(order.getAccountId(), 55000, "TOPUP_BUNDLE_PAYOS",
                        String.valueOf(order.getPayosOrderCode()), "ORDER:" + order.getId());
        doThrow(new IllegalStateException("broker offline")).when(events).sendTopupSuccessEvent(any());
        service.handlePayosWebhook(webhook(order));
        var saved = orders.findById(order.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.PENDING);
        assertThat(orders.findByStatusAndFulfillmentStatusOrderByPayosOrderCodeAsc(
                OrderStatus.PAID, OrderFulfillmentStatus.PENDING)).extracting(Order::getId).contains(order.getId());
        service.reconcileOrder(order.getId());
        assertThat(orders.findById(order.getId()).orElseThrow().getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.FULFILLED);
        verify(wallet, times(2)).creditVipCoin(order.getAccountId(), 55000, "TOPUP_BUNDLE_PAYOS",
                String.valueOf(order.getPayosOrderCode()), "ORDER:" + order.getId());
    }

    @Test
    void webhookAndWorkerSerializeOnTheSamePostgresOrderRow() throws Exception {
        var order = order(OrderStatus.PENDING);
        var webhook = webhook(order);
        var enteredWallet = new CountDownLatch(1);
        var releaseWallet = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredWallet.countDown();
            if (!releaseWallet.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
            return null;
        }).when(wallet).creditVipCoin(any(), anyLong(), any(), any(), any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.handlePayosWebhook(webhook));
            try {
                assertThat(enteredWallet.await(5, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> service.reconcileOrder(order.getId()));
                assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                releaseWallet.countDown();
                first.get(8, TimeUnit.SECONDS);
                second.get(8, TimeUnit.SECONDS);
            } finally {
                releaseWallet.countDown();
            }
        }
        verify(wallet, times(1)).creditVipCoin(any(), anyLong(), any(), any(), any());
        assertThat(orders.findById(order.getId()).orElseThrow().getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.FULFILLED);
    }

    @Test
    void remoteCreditBeforeALocalRollbackIsRecoveredUsingTheOriginalIdempotencyKey() {
        var order = order(OrderStatus.PAID);
        var remoteLedger = new ConcurrentHashMap<String, Long>();
        doAnswer(invocation -> {
            remoteLedger.putIfAbsent(invocation.getArgument(4), invocation.getArgument(1));
            return null;
        }).when(wallet).creditVipCoin(any(), anyLong(), any(), any(), any());
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.reconcileOrder(order.getId());
            throw new IllegalStateException("simulate local commit failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(orders.findById(order.getId()).orElseThrow().getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.PENDING);
        service.reconcileOrder(order.getId());
        assertThat(remoteLedger).containsOnlyKeys("ORDER:" + order.getId()).containsValue(55000L);
        assertThat(orders.findById(order.getId()).orElseThrow().getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.FULFILLED);
        verify(wallet, times(2)).creditVipCoin(order.getAccountId(), 55000, "TOPUP_BUNDLE_PAYOS",
                String.valueOf(order.getPayosOrderCode()), "ORDER:" + order.getId());
    }

    @Test
    void payosTimeoutLeavesAnOverdueOrderPendingUntilPaymentCanBeConfirmed() {
        var order = order(OrderStatus.PENDING);
        var api = mock(PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(api);
        when(api.get(order.getPayosOrderCode())).thenThrow(new IllegalStateException("provider timeout"));
        service.reconcileOrder(order.getId());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        var paid = new PaymentLink();
        paid.setOrderCode(order.getPayosOrderCode());
        paid.setAmount(order.getAmountVnd());
        paid.setAmountPaid(order.getAmountVnd());
        paid.setStatus(PaymentLinkStatus.PAID);
        doReturn(paid).when(api).get(order.getPayosOrderCode());
        service.reconcileOrder(order.getId());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(orders.findById(order.getId()).orElseThrow().getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.FULFILLED);
    }

    @Test
    void migrationPreservesLegacyPaymentsWithoutAutomaticallyCreditingThem() {
        String schema = "legacy_recovery";
        Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                .locations("classpath:db/migration").target("21").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        UUID paid = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        for (var entry : java.util.Map.of(paid, "PAID", pending, "PENDING").entrySet()) {
            jdbc.update("INSERT INTO legacy_recovery.orders(id,account_id,amount_vnd,payos_order_code,status) VALUES (?,?,?,?,?)",
                    entry.getKey(), UUID.randomUUID(), 50000L, CODES.incrementAndGet(), entry.getValue());
        }
        Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                .locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("SELECT fulfillment_status FROM legacy_recovery.orders WHERE id=?", String.class, paid))
                .isEqualTo("REVIEW_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM legacy_recovery.orders WHERE id=?", String.class, paid)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT fulfillment_status FROM legacy_recovery.orders WHERE id=?", String.class, pending))
                .isEqualTo("PENDING");
        verifyNoInteractions(wallet, events);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Order.class)
    @EnableJpaRepositories(basePackageClasses = OrderRepository.class)
    @Import(OrderServiceImpl.class)
    static class Config {
        @Bean @ServiceConnection
        PostgreSQLContainer database() {
            return new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("shop_payment_test")
                    .withUsername("shop_payment_test").withPassword("fixture_only");
        }
        @Bean WalletClient wallet() { return mock(WalletClient.class); }
        @Bean PayOS payOS() { return mock(PayOS.class); }
        @Bean TopupEventProducer events() { return mock(TopupEventProducer.class); }
        @Bean OrderMapper mapper() { return Mappers.getMapper(OrderMapper.class); }
        @Bean OrderValidator validator() { return mock(OrderValidator.class); }
        @Bean PaymentConfigService configs() { return mock(PaymentConfigService.class); }
        @Bean PaymentIdempotencyService cache() { return mock(PaymentIdempotencyService.class); }
        @Bean PaymentProperties properties() { return PaymentProperties.builder().expireMinutes(5).build(); }
    }
}
