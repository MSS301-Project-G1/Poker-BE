package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.CreatePaymentConfigRequest;
import com.msspoker.shopservice.dto.request.UpdatePaymentConfigRequest;
import com.msspoker.shopservice.dto.response.PaymentConfigResponse;
import com.msspoker.shopservice.entity.PaymentConfig;

import java.util.List;
import java.util.UUID;

public interface PaymentConfigService {

    List<PaymentConfigResponse> getAllConfigs();

    PaymentConfigResponse getActiveConfig();

    PaymentConfig getActiveEntity();

    PaymentConfigResponse switchConfig(UUID id);

    PaymentConfigResponse createConfig(CreatePaymentConfigRequest request);

    PaymentConfigResponse updateConfig(UUID id, UpdatePaymentConfigRequest request);
}
