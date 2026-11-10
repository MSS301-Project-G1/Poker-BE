package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.config.InternalShopSecurityConfig;
import com.msspoker.shopservice.security.InternalShopFilter;
import com.msspoker.shopservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalShopSecurityTest {
    private static final String KEY = "shop-private-test-key";
    private final InventoryService inventory = mock(InventoryService.class);
    private final String account = UUID.randomUUID().toString();
    private final String product = UUID.randomUUID().toString();

    private MockMvc client(String key) {
        var registration = new InternalShopSecurityConfig().internalShopFilter(key);
        assertThat(registration.getUrlPatterns()).containsExactly("/internal", "/internal/*");
        return MockMvcBuilders.standaloneSetup(new InternalInventoryController(inventory), new PublicProbe())
                .addFilter(registration.getFilter(), registration.getUrlPatterns().toArray(String[]::new)).build();
    }

    @Test
    void missingWrongOrWalletKeyCannotReadOrGrantEvenWithForgedAdmin() throws Exception {
        var mvc = client(KEY);
        for (String key : List.of("", "wrong", "wallet-private-test-key")) {
            mvc.perform(post("/internal/inventory/grant").header(InternalShopFilter.HEADER, key)
                    .header("X-User-Role", "ADMIN").header("X-User-Id", account))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SHOP_INTERNAL_FORBIDDEN"));
            for (String operation : List.of("owns", "sounds")) {
                mvc.perform(get("/internal/inventory/" + account + "/" + operation + "/" + product)
                        .header(InternalShopFilter.HEADER, key)).andExpect(status().isForbidden());
            }
        }
        mvc.perform(post("/internal/inventory/grant")).andExpect(status().isForbidden());
        verifyNoInteractions(inventory);
    }

    @Test
    void correctKeyAllowsBothReadsAndGrant() throws Exception {
        var mvc = client(KEY);
        mvc.perform(get("/internal/inventory/" + account + "/owns/" + product).header(InternalShopFilter.HEADER, KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/inventory/" + account + "/sounds/" + product).header(InternalShopFilter.HEADER, KEY))
                .andExpect(status().isOk());
        mvc.perform(post("/internal/inventory/grant").header(InternalShopFilter.HEADER, KEY).contentType("application/json")
                .content("{\"accountId\":\"" + account + "\",\"productId\":\"" + product + "\",\"idempotencyKey\":\"reward:key\"}"))
                .andExpect(status().isOk());
        verify(inventory).checkOwnership(UUID.fromString(account), UUID.fromString(product));
        verify(inventory).getOwnedSound(UUID.fromString(account), UUID.fromString(product));
        verify(inventory).grantItem(any());
    }

    @Test
    void emptyConfigAndDuplicateHeadersAreRejected() throws Exception {
        client("").perform(get("/internal/inventory/" + account + "/owns/" + product)
                .header(InternalShopFilter.HEADER, KEY)).andExpect(status().isForbidden());
        client(KEY).perform(get("/internal/inventory/" + account + "/owns/" + product)
                .header(InternalShopFilter.HEADER, KEY, "other")).andExpect(status().isForbidden());
        verifyNoInteractions(inventory);
    }

    @Test
    void internalFilterDoesNotGatePublicShopOrPayosWebhook() throws Exception {
        var mvc = client(KEY);
        mvc.perform(get("/api/shop/products")).andExpect(status().isOk());
        mvc.perform(post("/api/payments/payos/webhook")).andExpect(status().isOk());
        verifyNoInteractions(inventory);
    }

    @RestController
    static class PublicProbe {
        @GetMapping("/api/shop/products") String products() { return "public"; }
        @PostMapping("/api/payments/payos/webhook") String webhook() { return "public"; }
    }
}
