package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.dto.request.CreatePaymentConfigRequest;
import com.msspoker.shopservice.dto.request.UpdatePaymentConfigRequest;
import com.msspoker.shopservice.dto.response.PaymentConfigResponse;
import com.msspoker.shopservice.entity.PaymentConfig;
import com.msspoker.shopservice.enums.PaymentProvider;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.mapper.PaymentConfigMapper;
import com.msspoker.shopservice.repository.PaymentConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfigServiceImplTest {

    @Mock
    private PaymentConfigRepository paymentConfigRepository;

    @Spy
    private PaymentConfigMapper paymentConfigMapper = Mappers.getMapper(PaymentConfigMapper.class);

    private PaymentConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentConfigServiceImpl(paymentConfigRepository, paymentConfigMapper);
    }

    @Test
    @DisplayName("getAllConfigs - Trả về danh sách cấu hình")
    void getAllConfigs_Success() {
        PaymentConfig config = PaymentConfig.builder()
                .id(UUID.randomUUID())
                .provider(PaymentProvider.PAYOS)
                .name("PayOS")
                .active(true)
                .main(true)
                .build();

        when(paymentConfigRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(config));

        List<PaymentConfigResponse> result = service.getAllConfigs();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("PayOS");
        assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("switchConfig - Kích hoạt config thành công và hủy các config khác")
    void switchConfig_Success() {
        UUID configId = UUID.randomUUID();
        PaymentConfig bankConfig = PaymentConfig.builder()
                .id(configId)
                .provider(PaymentProvider.MANUAL_BANK)
                .name("MB Bank")
                .bankCode("MB")
                .accountNumber("0348732999")
                .active(false)
                .main(false)
                .build();

        when(paymentConfigRepository.findById(configId)).thenReturn(Optional.of(bankConfig));
        when(paymentConfigRepository.save(any(PaymentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentConfigResponse result = service.switchConfig(configId);

        verify(paymentConfigRepository).deactivateAll();
        assertThat(result.isActive()).isTrue();
        assertThat(bankConfig.isActive()).isTrue();
    }

    @Test
    @DisplayName("switchConfig - Thất bại: Không tìm thấy config")
    void switchConfig_NotFound() {
        UUID randomId = UUID.randomUUID();
        when(paymentConfigRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.switchConfig(randomId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SHOP_PAYMENT_CONFIG_NOT_FOUND");
    }

    @Test
    @DisplayName("createConfig - Tạo mới phương thức thành công")
    void createConfig_Success() {
        CreatePaymentConfigRequest req = CreatePaymentConfigRequest.builder()
                .provider(PaymentProvider.MANUAL_BANK)
                .name("Vietcombank")
                .bankCode("VCB")
                .accountNumber("9999999999")
                .accountHolder("POKER ADMIN")
                .build();

        when(paymentConfigRepository.save(any(PaymentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentConfigResponse result = service.createConfig(req);
        assertThat(result.name()).isEqualTo("Vietcombank");
        assertThat(result.bankCode()).isEqualTo("VCB");
        assertThat(result.accountNumber()).isEqualTo("9999999999");
    }

    @Test
    @DisplayName("updateConfig - Cập nhật cấu hình thành công")
    void updateConfig_Success() {
        UUID id = UUID.randomUUID();
        PaymentConfig config = PaymentConfig.builder()
                .id(id)
                .provider(PaymentProvider.MANUAL_BANK)
                .name("Old Bank")
                .bankCode("MB")
                .accountNumber("123456")
                .build();

        UpdatePaymentConfigRequest req = UpdatePaymentConfigRequest.builder()
                .name("Updated Bank")
                .bankCode("VCB")
                .accountNumber("654321")
                .accountHolder("NEW HOLDER")
                .build();

        when(paymentConfigRepository.findById(id)).thenReturn(Optional.of(config));
        when(paymentConfigRepository.save(any(PaymentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentConfigResponse result = service.updateConfig(id, req);
        assertThat(result.name()).isEqualTo("Updated Bank");
        assertThat(result.bankCode()).isEqualTo("VCB");
        assertThat(result.accountNumber()).isEqualTo("654321");
    }
}
