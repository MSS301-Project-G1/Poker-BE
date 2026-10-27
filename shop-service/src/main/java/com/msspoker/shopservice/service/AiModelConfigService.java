package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.request.UpdateAiModelConfigRequest;
import com.msspoker.shopservice.dto.response.AiModelConfigResponse;
import com.msspoker.shopservice.enums.AiProviderType;

import java.util.List;
import java.util.UUID;

public interface AiModelConfigService {

    List<AiModelConfigResponse> getAllConfigs();

    List<AiModelConfigResponse> getActiveConfigs();

    AiModelConfigResponse getDefaultConfig();

    AiModelConfigResponse createConfig(CreateAiModelConfigRequest request);

    AiModelConfigResponse updateConfig(UUID id, UpdateAiModelConfigRequest request);

    AiModelConfigResponse setDefaultConfig(UUID id);

    AiModelConfigResponse toggleActive(UUID id, boolean active);

    void deleteConfig(UUID id);

    List<AiModelConfigResponse> syncFromProvider(AiProviderType providerType);
}
