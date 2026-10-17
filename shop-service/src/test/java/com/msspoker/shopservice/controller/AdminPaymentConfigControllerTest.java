package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreatePaymentConfigRequest;
import com.msspoker.shopservice.dto.request.UpdatePaymentConfigRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.PaymentConfigResponse;
import com.msspoker.shopservice.enums.PaymentProvider;
import com.msspoker.shopservice.service.PaymentConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPaymentConfigControllerTest {

    @Mock
    private PaymentConfigService paymentConfigService;

    private AdminPaymentConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminPaymentConfigController(paymentConfigService);
    }

    @Test
    @DisplayName("getAllConfigs - Thành công")
    void getAllConfigs_Success() {
        PaymentConfigResponse resp = PaymentConfigResponse.builder()
                .id(UUID.randomUUID())
                .name("PayOS")
                .provider(PaymentProvider.PAYOS)
                .isActive(true)
                .build();

        when(paymentConfigService.getAllConfigs()).thenReturn(List.of(resp));

        ResponseEntity<ApiResponse<List<PaymentConfigResponse>>> response = controller.getAllConfigs();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("switchConfig - Thành công")
    void switchConfig_Success() {
        UUID id = UUID.randomUUID();
        PaymentConfigResponse resp = PaymentConfigResponse.builder()
                .id(id)
                .name("MB Bank")
                .provider(PaymentProvider.MANUAL_BANK)
                .isActive(true)
                .build();

        when(paymentConfigService.switchConfig(id)).thenReturn(resp);

        ResponseEntity<ApiResponse<PaymentConfigResponse>> response = controller.switchConfig(id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().isActive()).isTrue();
        verify(paymentConfigService).switchConfig(eq(id));
    }

    @Test
    @DisplayName("createConfig - Thành công")
    void createConfig_Success() {
        CreatePaymentConfigRequest req = CreatePaymentConfigRequest.builder()
                .provider(PaymentProvider.MANUAL_BANK)
                .name("Vietcombank")
                .build();

        PaymentConfigResponse resp = PaymentConfigResponse.builder()
                .id(UUID.randomUUID())
                .provider(PaymentProvider.MANUAL_BANK)
                .name("Vietcombank")
                .build();

        when(paymentConfigService.createConfig(req)).thenReturn(resp);

        ResponseEntity<ApiResponse<PaymentConfigResponse>> response = controller.createConfig(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().name()).isEqualTo("Vietcombank");
    }

    @Test
    @DisplayName("updateConfig - Thành công")
    void updateConfig_Success() {
        UUID id = UUID.randomUUID();
        UpdatePaymentConfigRequest req = UpdatePaymentConfigRequest.builder()
                .name("Updated Bank")
                .build();

        PaymentConfigResponse resp = PaymentConfigResponse.builder()
                .id(id)
                .name("Updated Bank")
                .build();

        when(paymentConfigService.updateConfig(id, req)).thenReturn(resp);

        ResponseEntity<ApiResponse<PaymentConfigResponse>> response = controller.updateConfig(id, req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().name()).isEqualTo("Updated Bank");
    }
}
