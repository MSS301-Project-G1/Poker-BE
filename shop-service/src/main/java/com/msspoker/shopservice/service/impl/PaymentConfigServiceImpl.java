package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.dto.request.CreatePaymentConfigRequest;
import com.msspoker.shopservice.dto.request.UpdatePaymentConfigRequest;
import com.msspoker.shopservice.dto.response.PaymentConfigResponse;
import com.msspoker.shopservice.entity.PaymentConfig;
import com.msspoker.shopservice.enums.PaymentProvider;
import com.msspoker.shopservice.exception.OrderExceptions;
import com.msspoker.shopservice.mapper.PaymentConfigMapper;
import com.msspoker.shopservice.repository.PaymentConfigRepository;
import com.msspoker.shopservice.service.PaymentConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfigServiceImpl implements PaymentConfigService {

    private final PaymentConfigRepository paymentConfigRepository;
    private final PaymentConfigMapper paymentConfigMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentConfigResponse> getAllConfigs() {
        log.debug("Fetching all payment configurations");
        List<PaymentConfig> configs = paymentConfigRepository.findAllByOrderByCreatedAtAsc();
        return paymentConfigMapper.toPaymentConfigResponseList(configs);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentConfigResponse getActiveConfig() {
        PaymentConfig active = getActiveEntity();
        return paymentConfigMapper.toPaymentConfigResponse(active);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentConfig getActiveEntity() {
        return paymentConfigRepository.findByActiveTrue()
                .orElseGet(() -> {
                    log.warn("No active payment config found. Falling back to default PayOS.");
                    return PaymentConfig.builder()
                            .provider(PaymentProvider.PAYOS)
                            .name("Cổng PayOS Mặc Định")
                            .active(true)
                            .main(true)
                            .build();
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentConfigResponse switchConfig(UUID id) {
        log.info("Admin switching active payment config to id={}", id);
        PaymentConfig targetConfig = paymentConfigRepository.findById(id)
                .orElseThrow(OrderExceptions::paymentConfigNotFound);

        // Deactivate all configs
        paymentConfigRepository.deactivateAll();

        // Activate target config
        targetConfig.setActive(true);
        PaymentConfig saved = paymentConfigRepository.save(targetConfig);
        log.info("Switched active payment method to provider={}, name={}", saved.getProvider(), saved.getName());

        return paymentConfigMapper.toPaymentConfigResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentConfigResponse createConfig(CreatePaymentConfigRequest request) {
        log.info("Admin creating new payment configuration: provider={}, name={}", request.provider(), request.name());
        PaymentConfig config = paymentConfigMapper.toPaymentConfig(request);
        PaymentConfig saved = paymentConfigRepository.save(config);
        log.info("Created payment configuration with id={}", saved.getId());
        return paymentConfigMapper.toPaymentConfigResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentConfigResponse updateConfig(UUID id, UpdatePaymentConfigRequest request) {
        log.info("Admin updating payment configuration id={}", id);
        PaymentConfig config = paymentConfigRepository.findById(id)
                .orElseThrow(OrderExceptions::paymentConfigNotFound);

        paymentConfigMapper.updatePaymentConfigFromRequest(request, config);
        PaymentConfig updated = paymentConfigRepository.save(config);
        log.info("Updated payment configuration id={}", updated.getId());
        return paymentConfigMapper.toPaymentConfigResponse(updated);
    }
}
