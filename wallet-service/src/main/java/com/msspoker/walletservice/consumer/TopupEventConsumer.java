package com.msspoker.walletservice.consumer;

import com.msspoker.walletservice.config.RabbitMQConfig;
import com.msspoker.walletservice.dto.request.CreditRequest;
import com.msspoker.walletservice.enums.CurrencyType;
import com.msspoker.walletservice.enums.TransactionReason;
import com.msspoker.walletservice.dto.event.VipCoinTopupSuccessEvent;
import com.msspoker.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TopupEventConsumer {

    private final WalletService walletService;

    @RabbitListener(queues = RabbitMQConfig.TOPUP_QUEUE_NAME)
    public void handleVipCoinTopup(VipCoinTopupSuccessEvent event) {
        log.info("Received VipCoinTopupSuccessEvent: orderId={}, accountId={}, vipCoinAmount={}, orderCode={}",
                event.orderId(), event.accountId(), event.vipCoinAmount(), event.payosOrderCode());

        CreditRequest creditRequest = CreditRequest.builder()
                .accountId(event.accountId())
                .amount(event.vipCoinAmount())
                .currencyType(CurrencyType.VIP_COIN)
                .reason(TransactionReason.TOPUP_BUNDLE_PAYOS)
                .refId(String.valueOf(event.payosOrderCode()))
                .idempotencyKey("ORDER:" + event.orderId())
                .build();

        try {
            walletService.credit(creditRequest);
            log.info("Successfully credited {} VIP Coins to accountId={} for orderId={}",
                    event.vipCoinAmount(), event.accountId(), event.orderId());
        } catch (Exception ex) {
            log.error("Failed to process VIP coin top-up for orderId={}, accountId={}: {}",
                    event.orderId(), event.accountId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
