package com.msspoker.shopservice.security;

import com.msspoker.shopservice.config.InternalShopSecurityConfig;
import com.msspoker.shopservice.config.SecurityConfig;
import com.msspoker.shopservice.config.ShopGatewaySecurityConfig;
import com.msspoker.shopservice.config.WebConfig;
import com.msspoker.shopservice.controller.AdminPaymentConfigController;
import com.msspoker.shopservice.controller.InternalInventoryController;
import com.msspoker.shopservice.controller.PaymentWebhookController;
import com.msspoker.shopservice.controller.ProductController;
import com.msspoker.shopservice.exception.GlobalExceptionHandler;
import com.msspoker.shopservice.exception.OrderExceptions;
import com.msspoker.shopservice.service.InventoryService;
import com.msspoker.shopservice.service.OrderService;
import com.msspoker.shopservice.service.PaymentConfigService;
import com.msspoker.shopservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ShopGatewaySecurityTest.TestConfig.class)
@WebAppConfiguration
class ShopGatewaySecurityTest {
    private static final String KEY = "shop-gateway-test-key";
    private static final String INTERNAL_KEY = "shop-internal-test-key";
    private static final String WEBHOOK = "/api/payments/payos/webhook";
    private static final String WEBHOOK_BODY = """
            {"code":"00","desc":"success","success":true,"signature":"fixture",
             "data":{"orderCode":123,"amount":3000,"description":"VQRIO123",
                     "accountNumber":"12345678","reference":"fixture-ref",
                     "transactionDateTime":"2026-11-10 12:00:00","currency":"VND",
                     "paymentLinkId":"fixture-link","code":"00","desc":"success"}}
            """;
    private final UUID account = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();

    @Autowired private WebApplicationContext context;
    @Autowired private ProductService products;
    @Autowired private PaymentConfigService payments;
    @Autowired private OrderService orders;
    @Autowired private InventoryService inventory;
    private MockMvc mvc;

    private MockMvc client(String key) {
        var gateway = new ShopGatewaySecurityConfig().shopGatewayFilter(key);
        var internal = new InternalShopSecurityConfig().internalShopFilter(INTERNAL_KEY);
        assertThat(gateway.getUrlPatterns()).containsExactly("/api", "/api/*");
        assertThat(gateway.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilter(gateway.getFilter(), gateway.getUrlPatterns().toArray(String[]::new))
                .addFilter(internal.getFilter(), internal.getUrlPatterns().toArray(String[]::new))
                .apply(springSecurity()).build();
    }

    @BeforeEach
    void setUp() {
        reset(products, payments, orders, inventory);
        mvc = client(KEY);
    }

    @Test
    void forgedIdentityWithoutValidGatewayKeyCannotReadAdminDataOrPurchase() throws Exception {
        for (String key : List.of("", "wrong", INTERNAL_KEY)) {
            mvc.perform(get("/api/admin/payments/methods").header(ShopGatewayFilter.HEADER, key)
                    .header("X-User-Id", account).header("X-User-Role", "ADMIN"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SHOP_FORBIDDEN"));
            mvc.perform(post("/api/shop/products/" + product + "/purchase").header(ShopGatewayFilter.HEADER, key)
                    .header("X-User-Id", account).header("X-User-Role", "USER"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/payments/methods").header("X-User-Id", account).header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(products, payments, orders, inventory);
    }

    @Test
    void gatewayIdentityPreservesUserPurchaseAndAdminAuthorization() throws Exception {
        mvc.perform(post("/api/shop/products/" + product + "/purchase").header(ShopGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).header("X-User-Role", "USER")).andExpect(status().isOk());
        verify(products).purchaseProduct(account, product);
        mvc.perform(get("/api/admin/payments/methods").header(ShopGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).header("X-User-Role", "USER")).andExpect(status().isForbidden());
        verifyNoInteractions(payments);
        mvc.perform(get("/api/admin/payments/methods").header(ShopGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).header("X-User-Role", "ADMIN")).andExpect(status().isOk());
        verify(payments).getAllConfigs();
    }

    @Test
    void catalogRemainsAnonymousThroughGatewayAndCanUseVerifiedOwnershipIdentity() throws Exception {
        String path = "/api/shop/products/" + product;
        mvc.perform(get(path)).andExpect(status().isForbidden());
        mvc.perform(get(path).header(ShopGatewayFilter.HEADER, KEY)).andExpect(status().isOk());
        verify(products).getProductDetail(product, null);
        mvc.perform(get(path).header(ShopGatewayFilter.HEADER, KEY).header("X-User-Id", account)
                .header("X-User-Role", "USER")).andExpect(status().isOk());
        verify(products).getProductDetail(product, account);
        mvc.perform(post(path + "/purchase").header(ShopGatewayFilter.HEADER, KEY)).andExpect(status().isUnauthorized());
        verify(products, never()).purchaseProduct(any(), any());
    }

    @Test
    void emptyConfigurationAndDuplicateHeadersFailClosed() throws Exception {
        client("").perform(get("/api/shop/products/" + product).header(ShopGatewayFilter.HEADER, KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/shop/products/" + product).header(ShopGatewayFilter.HEADER, KEY, KEY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(products);
    }

    @Test
    void internalAndGatewayCredentialsRemainIndependent() throws Exception {
        mvc.perform(get("/api/shop/products/" + product).header(InternalShopFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isForbidden());
        String path = "/internal/inventory/" + account + "/owns/" + product;
        mvc.perform(get(path).header(ShopGatewayFilter.HEADER, KEY)).andExpect(status().isForbidden());
        mvc.perform(get(path).header(InternalShopFilter.HEADER, INTERNAL_KEY)).andExpect(status().isOk());
        verify(inventory).checkOwnership(account, product);
        verifyNoInteractions(products);
    }

    @Test
    void exactPayosCallbackReachesSignatureHandlerWithoutIdentityOrGatewayKey() throws Exception {
        doAnswer(invocation -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getPrincipal()).isNotInstanceOf(UUID.class);
            assertThat(authentication.getAuthorities()).noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            return null;
        }).when(orders).handlePayosWebhook(any());
        mvc.perform(post(WEBHOOK).header("X-User-Id", account).header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(WEBHOOK_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.error").value(0));
        verify(orders).handlePayosWebhook(any());

        doThrow(OrderExceptions.invalidWebhookSignature()).when(orders).handlePayosWebhook(any());
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(WEBHOOK_BODY.replace("fixture", "invalid")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SHOP_PAYOS_INVALID_SIGNATURE"));
    }

    @Test
    void webhookExceptionDoesNotApplyToOtherPathsOrMethods() throws Exception {
        for (String path : List.of(WEBHOOK + "/", WEBHOOK + "/other", "/api/payments/orders")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(WEBHOOK)).andExpect(status().isForbidden());
        mvc.perform(put(WEBHOOK)).andExpect(status().isForbidden());
        verifyNoInteractions(orders);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, GatewayAuthenticationFilter.class, WebConfig.class, CurrentUserArgumentResolver.class,
            ProductController.class, AdminPaymentConfigController.class, PaymentWebhookController.class,
            InternalInventoryController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean ProductService products() { return mock(ProductService.class); }
        @Bean PaymentConfigService payments() { return mock(PaymentConfigService.class); }
        @Bean OrderService orders() { return mock(OrderService.class); }
        @Bean InventoryService inventory() { return mock(InventoryService.class); }
    }
}
