package com.msspoker.shopservice.producer;

import com.msspoker.shopservice.config.RabbitMQConfig;
import com.msspoker.shopservice.event.VipCoinTopupSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TopupEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendTopupSuccessEvent(VipCoinTopupSuccessEvent event) {
        log.info("Publishing VipCoinTopupSuccessEvent for orderId={}, accountId={}, vipCoinAmount={}",
                event.orderId(), event.accountId(), event.vipCoinAmount());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.TOPUP_ROUTING_KEY, event);
    }
}
