package com.msspoker.walletservice.controller;

import com.msspoker.walletservice.config.InternalWalletSecurityConfig;
import com.msspoker.walletservice.config.WalletGatewaySecurityConfig;
import com.msspoker.walletservice.security.WalletGatewayFilter;
import com.msspoker.walletservice.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletGatewaySecurityTest {
    private static final String KEY = "wallet-gateway-test-key";
    private final WalletService service = mock(WalletService.class);
    private final UUID account = UUID.randomUUID();

    private MockMvc client(String key) {
        var api = new WalletGatewaySecurityConfig().walletGatewayFilter(key);
        var internal = new InternalWalletSecurityConfig().internalWalletFilter("internal-test-key");
        assertThat(api.getUrlPatterns()).containsExactly("/api", "/api/*");
        return MockMvcBuilders.standaloneSetup(new WalletController(service), new InternalWalletController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .addFilter(api.getFilter(), api.getUrlPatterns().toArray(String[]::new))
                .addFilter(internal.getFilter(), internal.getUrlPatterns().toArray(String[]::new)).build();
    }

    @Test
    void forgedIdentityWithoutGatewayKeyCannotReadWalletOrTransactions() throws Exception {
        var mvc = client(KEY);
        for (String path : List.of("/api/wallet/me", "/api/wallet/me/transactions")) {
            mvc.perform(get(path).header("X-User-Id", account).header("X-User-Role", "ADMIN"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WALLET_FORBIDDEN"));
            mvc.perform(get(path).header("X-User-Id", account).header(WalletGatewayFilter.HEADER, "wrong"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void correctGatewayKeyAllowsVerifiedIdentityAndPreservesPagination() throws Exception {
        var mvc = client(KEY);
        mvc.perform(get("/api/wallet/me").header("X-User-Id", account).header(WalletGatewayFilter.HEADER, KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/wallet/me/transactions?page=2&size=5").header("X-User-Id", account)
                .header(WalletGatewayFilter.HEADER, KEY)).andExpect(status().isOk());
        verify(service).getMyWallet(account);
        verify(service).getMyTransactions(eq(account), argThat((Pageable page) -> page.getPageNumber() == 2 && page.getPageSize() == 5));
    }

    @Test
    void blankConfigurationAndDuplicateHeadersFailClosed() throws Exception {
        client("").perform(get("/api/wallet/me").header("X-User-Id", account).header(WalletGatewayFilter.HEADER, KEY))
                .andExpect(status().isForbidden());
        client(KEY).perform(get("/api/wallet/me").header("X-User-Id", account).header(WalletGatewayFilter.HEADER, KEY, KEY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void gatewayAndInternalCredentialsAreNotInterchangeable() throws Exception {
        var mvc = client(KEY);
        mvc.perform(get("/api/wallet/me").header("X-User-Id", account).header("X-Wallet-Internal-Key", "internal-test-key"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/wallet/" + account + "/balance").header(WalletGatewayFilter.HEADER, KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/wallet/" + account + "/balance").header("X-Wallet-Internal-Key", "internal-test-key"))
                .andExpect(status().isOk());
        verify(service).getBalance(account);
        verifyNoMoreInteractions(service);
    }
}
