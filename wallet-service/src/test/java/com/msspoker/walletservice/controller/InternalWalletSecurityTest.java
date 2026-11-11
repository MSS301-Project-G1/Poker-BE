package com.msspoker.walletservice.controller;

import com.msspoker.walletservice.config.InternalWalletSecurityConfig;
import com.msspoker.walletservice.security.InternalWalletFilter;
import com.msspoker.walletservice.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalWalletSecurityTest {
    private static final String KEY = "wallet-test-private-key";
    private final WalletService service = mock(WalletService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = client(KEY);
    }

    private MockMvc client(String key) {
        var registration = new InternalWalletSecurityConfig().internalWalletFilter(key);
        assertThat(registration.getUrlPatterns()).containsExactly("/internal", "/internal/*");
        return MockMvcBuilders.standaloneSetup(new InternalWalletController(service), new WalletController(service))
                .addFilter(registration.getFilter(), registration.getUrlPatterns().toArray(String[]::new)).build();
    }

    @Test
    void rejectsEveryInternalOperationBeforeBusinessCodeEvenWithForgedIdentity() throws Exception {
        for (String key : List.of("", "wrong")) {
            for (String path : List.of("credit", "debit")) {
                mvc.perform(post("/internal/wallet/" + path).header(InternalWalletFilter.HEADER, key)
                                .header("X-User-Role", "ADMIN").header("X-User-Id", UUID.randomUUID()))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WALLET_FORBIDDEN"));
            }
            for (String path : List.of("/internal/wallet/" + UUID.randomUUID() + "/balance",
                    "/internal/wallet/transactions/by-idempotency-key?key=transaction")) {
                mvc.perform(get(path).header(InternalWalletFilter.HEADER, key)).andExpect(status().isForbidden());
            }
        }
        mvc.perform(get("/internal/wallet/transactions/by-idempotency-key?key=x")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void correctKeyAllowsCreditDebitAndReads() throws Exception {
        var id = UUID.randomUUID();
        String body = "{\"accountId\":\"" + id + "\",\"amount\":10,\"reason\":\"DAILY_CHECKIN\",\"idempotencyKey\":\"test-key\"}";
        for (String path : List.of("credit", "debit")) {
            mvc.perform(post("/internal/wallet/" + path).header(InternalWalletFilter.HEADER, KEY)
                    .contentType("application/json").content(body)).andExpect(status().isOk());
        }
        mvc.perform(get("/internal/wallet/" + id + "/balance").header(InternalWalletFilter.HEADER, KEY)).andExpect(status().isOk());
        mvc.perform(get("/internal/wallet/transactions/by-idempotency-key?key=transaction").header(InternalWalletFilter.HEADER, KEY))
                .andExpect(status().isOk());
        verify(service).credit(any());
        verify(service).debit(any());
        verify(service).getBalance(id);
        verify(service).getTransactionByIdempotencyKey("transaction");
    }

    @Test
    void emptyConfigurationAndDuplicateKeysFailClosed() throws Exception {
        client("").perform(get("/internal/wallet/transactions/by-idempotency-key?key=x")
                .header(InternalWalletFilter.HEADER, KEY)).andExpect(status().isForbidden());
        mvc.perform(get("/internal/wallet/transactions/by-idempotency-key?key=x")
                .header(InternalWalletFilter.HEADER, KEY, "wrong")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void publicWalletRouteIsOutsideThisInternalFilter() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(get("/api/wallet/me").header("X-User-Id", id)).andExpect(status().isOk());
        verify(service).getMyWallet(id);
    }
}
