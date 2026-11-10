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

    @Test
    @DisplayName("Jackson deserialization of PayOS Webhook test payload")
    void testJacksonDeserialization() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = "{\n" +
                "  \"code\": \"00\",\n" +
                "  \"desc\": \"success\",\n" +
                "  \"success\": true,\n" +
                "  \"data\": {\n" +
                "    \"orderCode\": 123,\n" +
                "    \"amount\": 3000,\n" +
                "    \"description\": \"VQRIO123\",\n" +
                "    \"accountNumber\": \"12345678\",\n" +
                "    \"reference\": \"TF230204212323\",\n" +
                "    \"transactionDateTime\": \"2023-02-04 18:25:00\",\n" +
                "    \"currency\": \"VND\",\n" +
                "    \"paymentLinkId\": \"124c33293c43417ab7879e14c8d9eb18\",\n" +
                "    \"code\": \"00\",\n" +
                "    \"desc\": \"Thành công\"\n" +
                "  },\n" +
                "  \"signature\": \"8d8640d802576397a1ce45ebda7f835055768ac7ad2e0bfb77f9b8f12cca4c7f\"\n" +
                "}";
        Webhook parsed = mapper.readValue(json, Webhook.class);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getCode()).isEqualTo("00");
        assertThat(parsed.getData()).isNotNull();
        assertThat(parsed.getData().getOrderCode()).isEqualTo(123L);
    }
}
