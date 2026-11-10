package com.msspoker.shopservice.client;

import com.msspoker.shopservice.client.impl.RestWalletClientImpl;
import com.msspoker.shopservice.exception.AppException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RestWalletClientContractTest {
    private static final String BASE = "http://wallet.test/internal/wallet/";
    private final UUID account = UUID.randomUUID();
    private RestWalletClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        var rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        client = new RestWalletClientImpl(rest);
        ReflectionTestUtils.setField(client, "walletServiceUrl", "http://wallet.test");
        ReflectionTestUtils.setField(client, "walletInternalKey", "wallet-private-test-key");
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void onlyExplicitInsufficientBalanceFromWalletMapsToInsufficientFunds() {
        server.expect(requestTo(BASE + "debit")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"WALLET_INSUFFICIENT_BALANCE\"}"));
        assertThatThrownBy(() -> client.debitVipCoin(account, 10, "item", "purchase"))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getErrorCode()).isEqualTo("WALLET_INSUFFICIENT_BALANCE");
                });
    }

    @Test
    void otherHttpErrorsAreDependencyFailuresInsteadOfInsufficientFunds() {
        var statuses = List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE);
        for (HttpStatus status : statuses) {
            server.expect(requestTo(BASE + "debit")).andRespond(withStatus(status)
                    .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"WALLET_VALIDATION_ERROR\"}"));
        }
        server.expect(requestTo(BASE + "debit")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"WALLET_INSUFFICIENT_BALANCE\"}"));
        for (int i = 0; i <= statuses.size(); i++) {
            unavailable(() -> client.debitVipCoin(account, 10, "item", "purchase"));
        }
    }

    @Test
    void unreadableDebitErrorDoesNotPretendTheUserHasInsufficientFunds() {
        server.expect(requestTo(BASE + "debit")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("not-json"));
        unavailable(() -> client.debitVipCoin(account, 10, "item", "purchase"));
    }

    @Test
    void creditFailuresDoNotExposeUpstreamDetailsInTheUserMessage() {
        server.expect(requestTo(BASE + "credit")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"WALLET_FORBIDDEN\",\"message\":\"private-fixture-detail\"}"));
        assertThatThrownBy(() -> client.creditVipCoin(account, 10, "REFUND", "item", "refund"))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getErrorCode()).isEqualTo("WALLET_SERVICE_UNAVAILABLE");
                    assertThat(ex.getMessage()).doesNotContain("private-fixture-detail", "wallet.test");
                });
    }

    @Test
    void successfulTransfersKeepTheirHeaderAmountAndIdempotencyContract() {
        for (String operation : List.of("debit", "credit")) {
            server.expect(requestTo(BASE + operation)).andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-Wallet-Internal-Key", "wallet-private-test-key"))
                    .andExpect(content().json("{\"accountId\":\"" + account
                            + "\",\"amount\":10,\"currencyType\":\"VIP_COIN\",\"reason\":\"SHOP_BUY_ITEM\",\"refId\":\"item\",\"idempotencyKey\":\"purchase\"}"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
        client.debitVipCoin(account, 10, "item", "purchase");
        client.creditVipCoin(account, 10, "SHOP_BUY_ITEM", "item", "purchase");
    }

    @Test
    void lookupReadsDataAndEncodesTheEntireIdempotencyKeyAsOneQueryValue() {
        String key = "purchase&key=other+value";
        server.expect(requestTo(BASE + "transactions/by-idempotency-key?key=purchase%26key%3Dother%2Bvalue"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-Wallet-Internal-Key", "wallet-private-test-key"))
                .andRespond(withSuccess("{\"code\":\"WALLET_SUCCESS\",\"data\":{\"amount\":-100,\"idempotencyKey\":\"" + key + "\"}}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.findDebitAmountByIdempotencyKey(key)).contains(100L);
    }

    @Test
    void onlyConfirmedTransactionNotFoundReturnsEmpty() {
        server.expect(requestTo(BASE + "transactions/by-idempotency-key?key=lookup"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"WALLET_TRANSACTION_NOT_FOUND\"}"));
        assertThat(client.findDebitAmountByIdempotencyKey("lookup")).isEmpty();
    }

    @Test
    void failedLookupNeverMasqueradesAsNoTransaction() {
        var statuses = List.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR);
        for (HttpStatus status : statuses) {
            server.expect(requestTo(BASE + "transactions/by-idempotency-key?key=lookup"))
                    .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"WALLET_FORBIDDEN\"}"));
        }
        for (int i = 0; i < statuses.size(); i++) {
            unavailable(() -> client.findDebitAmountByIdempotencyKey("lookup"));
        }
    }

    @Test
    void invalidLookupPayloadsCannotBeUsedAsARefundAmount() {
        var bodies = List.of("{}", "not-json", "{\"code\":\"WALLET_SUCCESS\",\"result\":{\"amount\":-10}}",
                "{\"code\":\"WALLET_SUCCESS\",\"data\":{\"amount\":-10,\"idempotencyKey\":\"other\"}}",
                "{\"code\":\"WALLET_SUCCESS\",\"data\":{\"amount\":10,\"idempotencyKey\":\"lookup\"}}",
                "{\"code\":\"WALLET_SUCCESS\",\"data\":{\"amount\":0,\"idempotencyKey\":\"lookup\"}}",
                "{\"code\":\"WALLET_SUCCESS\",\"data\":{\"amount\":-1.5,\"idempotencyKey\":\"lookup\"}}",
                "{\"code\":\"WALLET_SUCCESS\",\"data\":{\"amount\":-9223372036854775808,\"idempotencyKey\":\"lookup\"}}",
                "{\"code\":\"WALLET_SUCCESS\",\"data\":{\"amount\":-9223372036854775809,\"idempotencyKey\":\"lookup\"}}");
        for (String body : bodies) {
            server.expect(requestTo(BASE + "transactions/by-idempotency-key?key=lookup"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }
        for (int i = 0; i < bodies.size(); i++) {
            unavailable(() -> client.findDebitAmountByIdempotencyKey("lookup"));
        }
    }

    @Test
    void transportFailureIsADependencyErrorForEveryOperation() {
        server.expect(requestTo(BASE + "debit")).andRespond(withException(new SocketTimeoutException("fixture timeout")));
        server.expect(requestTo(BASE + "credit")).andRespond(withException(new SocketTimeoutException("fixture timeout")));
        server.expect(requestTo(BASE + "transactions/by-idempotency-key?key=lookup"))
                .andRespond(withException(new SocketTimeoutException("fixture timeout")));
        unavailable(() -> client.debitVipCoin(account, 10, "item", "purchase"));
        unavailable(() -> client.creditVipCoin(account, 10, "REFUND", "item", "refund"));
        unavailable(() -> client.findDebitAmountByIdempotencyKey("lookup"));
    }

    private void unavailable(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(ex.getErrorCode()).isEqualTo("WALLET_SERVICE_UNAVAILABLE");
        });
    }
}
