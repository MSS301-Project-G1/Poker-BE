package com.msspoker.shopservice.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.ai.provider.AiModelProvider;
import com.msspoker.shopservice.ai.provider.AiProviderRegistry;
import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.request.UpdateAiModelConfigRequest;
import com.msspoker.shopservice.dto.response.AiModelConfigResponse;
import com.msspoker.shopservice.dto.response.AiModelDescriptor;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiProviderType;
import com.msspoker.shopservice.repository.AiModelConfigRepository;
import com.msspoker.shopservice.service.AiModelConfigService;
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
    private final ObjectMapper objectMapper;

    @Override
    public List<AiModelConfigResponse> getAllConfigs() {
        return configRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<AiModelConfigResponse> getActiveConfigs() {
        return configRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AiModelConfigResponse getDefaultConfig() {
        return configRepository.findByIsDefaultTrue()
                .or(() -> configRepository.findByIsActiveTrue().stream().findFirst())
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public AiModelConfigResponse createConfig(CreateAiModelConfigRequest request) {
        log.info("Creating AI model config for provider={}, model={}", request.provider(), request.modelName());

        Optional<AiModelConfig> existing = configRepository.findByProviderAndModelName(request.provider(), request.modelName());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Model '" + request.modelName() + "' của nhà cung cấp " + request.provider() + " đã tồn tại");
        }

        String capsJson = serializeCapabilities(request.capabilities());
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        boolean isActive = request.isActive() == null || Boolean.TRUE.equals(request.isActive());

        AiModelConfig config = AiModelConfig.builder()
                .provider(request.provider())
                .modelName(request.modelName().trim())
                .displayName(request.displayName().trim())
                .capabilities(capsJson)
                .isActive(isActive)
                .isDefault(isDefault)
                .build();

        AiModelConfig saved = configRepository.save(config);

        if (isDefault) {
            configRepository.clearDefaultExcept(saved.getId());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public AiModelConfigResponse updateConfig(UUID id, UpdateAiModelConfigRequest request) {
        AiModelConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình AI model id=" + id));

        config.setDisplayName(request.displayName().trim());
        if (request.capabilities() != null) {
            config.setCapabilities(serializeCapabilities(request.capabilities()));
        }
        if (request.isActive() != null) {
            config.setActive(request.isActive());
        }
        if (Boolean.TRUE.equals(request.isDefault())) {
            config.setDefault(true);
            configRepository.clearDefaultExcept(id);
        }

        return toResponse(configRepository.save(config));
    }

    @Override
    @Transactional
    public AiModelConfigResponse setDefaultConfig(UUID id) {
        AiModelConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình AI model id=" + id));

        configRepository.clearDefaultExcept(id);
        config.setDefault(true);
        config.setActive(true);
        return toResponse(configRepository.save(config));
    }

    @Override
    @Transactional
    public AiModelConfigResponse toggleActive(UUID id, boolean active) {
        AiModelConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình AI model id=" + id));

        if (!active && config.isDefault()) {
            throw new IllegalStateException("Không thể vô hiệu hóa mô hình mặc định hiện tại. Hãy chọn mô hình khác làm mặc định trước.");
        }

        config.setActive(active);
        return toResponse(configRepository.save(config));
    }

    @Override
    @Transactional
    public void deleteConfig(UUID id) {
        AiModelConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình AI model id=" + id));

        if (config.isDefault()) {
            throw new IllegalStateException("Không thể xóa mô hình mặc định hiện tại.");
        }

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
                synced.add(toResponse(configRepository.save(c)));
            } else {
                List<String> caps = d.capabilities() != null
                        ? d.capabilities().stream().map(Enum::name).toList()
                        : List.of();
                AiModelConfig newConfig = AiModelConfig.builder()
                        .provider(d.provider())
                        .modelName(d.modelName())
                        .displayName(d.displayName())
                        .capabilities(serializeCapabilities(caps))
                        .isActive(true)
                        .isDefault(false)
                        .build();
                synced.add(toResponse(configRepository.save(newConfig)));
            }
        }
        return synced;
    }

    private AiModelConfigResponse toResponse(AiModelConfig entity) {
        return AiModelConfigResponse.builder()
                .id(entity.getId())
                .provider(entity.getProvider())
                .modelName(entity.getModelName())
                .displayName(entity.getDisplayName())
                .capabilities(deserializeCapabilities(entity.getCapabilities()))
                .isActive(entity.isActive())
                .isDefault(entity.isDefault())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String serializeCapabilities(List<String> caps) {
        if (caps == null || caps.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(caps);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> deserializeCapabilities(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
