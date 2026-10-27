package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.ai.provider.AiModelProvider;
import com.msspoker.shopservice.ai.provider.AiProviderRegistry;
import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.request.UpdateAiModelConfigRequest;
import com.msspoker.shopservice.dto.response.AiModelConfigResponse;
import com.msspoker.shopservice.dto.response.AiModelDescriptor;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiProviderType;
import com.msspoker.shopservice.mapper.AiModelConfigMapper;
import com.msspoker.shopservice.repository.AiModelConfigRepository;
import com.msspoker.shopservice.service.AiModelConfigService;
import com.msspoker.shopservice.validator.AiModelConfigValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiModelConfigServiceImpl implements AiModelConfigService {

    private final AiModelConfigRepository configRepository;
    private final AiProviderRegistry providerRegistry;
    private final AiModelConfigMapper modelConfigMapper;
    private final AiModelConfigValidator modelConfigValidator;

    @Override
    public List<AiModelConfigResponse> getAllConfigs() {
        return modelConfigMapper.toResponseList(configRepository.findAll());
    }

    @Override
    public List<AiModelConfigResponse> getActiveConfigs() {
        return modelConfigMapper.toResponseList(configRepository.findByIsActiveTrue());
    }

    @Override
    public AiModelConfigResponse getDefaultConfig() {
        return configRepository.findByIsDefaultTrue()
                .or(() -> configRepository.findByIsActiveTrue().stream().findFirst())
                .map(modelConfigMapper::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public AiModelConfigResponse createConfig(CreateAiModelConfigRequest request) {
        log.info("Creating AI model config for provider={}, model={}", request.provider(), request.modelName());

        modelConfigValidator.validateForCreate(request);

        AiModelConfig config = modelConfigMapper.toEntity(request);
        if (request.isActive() == null) {
            config.setActive(true);
        }

        AiModelConfig saved = configRepository.save(config);

        if (saved.isDefault()) {
            configRepository.clearDefaultExcept(saved.getId());
        }

        return modelConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AiModelConfigResponse updateConfig(UUID id, UpdateAiModelConfigRequest request) {
        AiModelConfig config = configRepository.findById(id).orElse(null);
        modelConfigValidator.validateForUpdate(id, request, config);

        config.setDisplayName(request.displayName().trim());
        if (request.capabilities() != null) {
            config.setCapabilities(modelConfigMapper.serializeCapabilities(request.capabilities()));
        }
        if (request.isActive() != null) {
            config.setActive(request.isActive());
        }
        if (Boolean.TRUE.equals(request.isDefault())) {
            config.setDefault(true);
            configRepository.clearDefaultExcept(id);
        }

        return modelConfigMapper.toResponse(configRepository.save(config));
    }

    @Override
    @Transactional
    public AiModelConfigResponse setDefaultConfig(UUID id) {
        AiModelConfig config = configRepository.findById(id).orElse(null);
        modelConfigValidator.validateExists(id, config);

        configRepository.clearDefaultExcept(id);
        config.setDefault(true);
        config.setActive(true);
        return modelConfigMapper.toResponse(configRepository.save(config));
    }

    @Override
    @Transactional
    public AiModelConfigResponse toggleActive(UUID id, boolean active) {
        AiModelConfig config = configRepository.findById(id).orElse(null);
        modelConfigValidator.validateExists(id, config);

        if (!active && config.isDefault()) {
            throw new IllegalStateException("Không thể vô hiệu hóa mô hình mặc định hiện tại. Hãy chọn mô hình khác làm mặc định trước.");
        }

        config.setActive(active);
        return modelConfigMapper.toResponse(configRepository.save(config));
    }

    @Override
    @Transactional
    public void deleteConfig(UUID id) {
        AiModelConfig config = configRepository.findById(id).orElse(null);
        modelConfigValidator.validateForDelete(id, config);

        configRepository.delete(config);
    }

    @Override
    @Transactional
    public List<AiModelConfigResponse> syncFromProvider(AiProviderType providerType) {
        log.info("Syncing available AI models from provider: {}", providerType);
        AiModelProvider provider = providerRegistry.getRequiredProvider(providerType);
        List<AiModelDescriptor> descriptors = provider.listAvailableModels();

        List<AiModelConfigResponse> synced = new ArrayList<>();
        for (AiModelDescriptor d : descriptors) {
            Optional<AiModelConfig> existing = configRepository.findByProviderAndModelName(d.provider(), d.modelName());
            if (existing.isPresent()) {
                AiModelConfig c = existing.get();
                c.setDisplayName(d.displayName());
                synced.add(modelConfigMapper.toResponse(configRepository.save(c)));
            } else {
                List<String> caps = d.capabilities() != null
                        ? d.capabilities().stream().map(Enum::name).toList()
                        : List.of();
                AiModelConfig newConfig = AiModelConfig.builder()
                        .provider(d.provider())
                        .modelName(d.modelName())
                        .displayName(d.displayName())
                        .capabilities(modelConfigMapper.serializeCapabilities(caps))
                        .isActive(true)
                        .isDefault(false)
                        .build();
                synced.add(modelConfigMapper.toResponse(configRepository.save(newConfig)));
            }
        }
        return synced;
    }
}
