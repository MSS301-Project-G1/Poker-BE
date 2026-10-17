package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.payos.model.webhooks.Webhook;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentWebhookController controller;

    @Test
    @DisplayName("handleWebhook - Chuyển payload cho OrderService và trả về response chuẩn của PayOS {error: 0, message: Ok}")
    void handleWebhook() {
        Webhook webhook = org.mockito.Mockito.mock(Webhook.class);
        when(webhook.getCode()).thenReturn("00");
        when(webhook.getDesc()).thenReturn("Success");

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(webhook);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo(0);
        assertThat(response.getBody().get("message")).isEqualTo("Ok");

        verify(orderService).handlePayosWebhook(webhook);
    }
}
