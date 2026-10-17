package com.msspoker.walletservice.consumer;

import com.msspoker.walletservice.dto.request.CreditRequest;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.enums.CurrencyType;
import com.msspoker.walletservice.enums.TransactionReason;
import com.msspoker.walletservice.event.VipCoinTopupSuccessEvent;
import com.msspoker.walletservice.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopupEventConsumerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private TopupEventConsumer consumer;

    private UUID orderId;
    private UUID accountId;
    private UUID bundleId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        bundleId = UUID.randomUUID();
    }

    @Test
    @DisplayName("handleVipCoinTopup - Tiếp nhận event nạp VIP Coin và gọi walletService.credit với đúng dữ liệu")
    void handleVipCoinTopup_Success() {
        VipCoinTopupSuccessEvent event = VipCoinTopupSuccessEvent.builder()
                .orderId(orderId)
                .accountId(accountId)
                .bundleId(bundleId)
                .vipCoinAmount(115000L)
                .payosOrderCode(200002L)
                .source("PAYOS_BUNDLE")
                .timestamp(Instant.now())
                .build();

        TransactionResponse txResp = TransactionResponse.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(115000L)
                .balanceAfter(115000L)
                .reason(TransactionReason.TOPUP_BUNDLE_PAYOS)
                .refId("200002")
                .idempotencyKey("ORDER:" + orderId)
                .currencyType(CurrencyType.VIP_COIN)
                .createdAt(Instant.now())
                .build();

        when(walletService.credit(any(CreditRequest.class))).thenReturn(txResp);

        consumer.handleVipCoinTopup(event);

        ArgumentCaptor<CreditRequest> captor = ArgumentCaptor.forClass(CreditRequest.class);
        verify(walletService).credit(captor.capture());

        CreditRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.accountId()).isEqualTo(accountId);
        assertThat(capturedRequest.amount()).isEqualTo(115000L);
        assertThat(capturedRequest.currencyType()).isEqualTo(CurrencyType.VIP_COIN);
        assertThat(capturedRequest.reason()).isEqualTo(TransactionReason.TOPUP_BUNDLE_PAYOS);
        assertThat(capturedRequest.refId()).isEqualTo("200002");
        assertThat(capturedRequest.idempotencyKey()).isEqualTo("ORDER:" + orderId);
    }
}
