package com.msspoker.shopservice.client;

import com.msspoker.shopservice.client.impl.RestWalletClientImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.UUID;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WalletInternalKeyTest {
    @Test
    void sendsPrivateKeyForDebitCreditAndTransactionLookup() {
        var rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        var client = new RestWalletClientImpl(rest);
        ReflectionTestUtils.setField(client, "walletServiceUrl", "http://wallet.test");
        ReflectionTestUtils.setField(client, "walletInternalKey", "shop-wallet-test-key");
        for (String path : new String[]{"debit", "credit", "transactions/by-idempotency-key?key=lookup"}) {
            server.expect(requestTo("http://wallet.test/internal/wallet/" + path))
                    .andExpect(header("X-Wallet-Internal-Key", "shop-wallet-test-key"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
        var account = UUID.randomUUID();
        client.debitVipCoin(account, 10, "ref", "debit");
        client.creditVipCoin(account, 10, "REFUND", "ref", "credit");
        client.findDebitAmountByIdempotencyKey("lookup");
        server.verify();
    }
}
