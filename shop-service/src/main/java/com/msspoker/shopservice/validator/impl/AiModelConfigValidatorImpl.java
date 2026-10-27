package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.request.UpdateAiModelConfigRequest;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.exception.AiModelExceptions;
import com.msspoker.shopservice.repository.AiModelConfigRepository;
import com.msspoker.shopservice.validator.AiModelConfigValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiModelConfigValidatorImpl implements AiModelConfigValidator {

    private final AiModelConfigRepository configRepository;

    @Override
    public void validateExists(UUID id, AiModelConfig config) {
        if (config == null) {
            throw AiModelExceptions.modelNotFound(id);
        }
    }

    @Override
    public void validateForCreate(CreateAiModelConfigRequest request) {
        if (configRepository.findByProviderAndModelName(request.provider(), request.modelName()).isPresent()) {
            throw AiModelExceptions.duplicateModel(request.provider().name(), request.modelName());
        }
    }

    @Override
    public void validateForUpdate(UUID id, UpdateAiModelConfigRequest request, AiModelConfig config) {
        validateExists(id, config);
    }

    @Override
    public void validateForDelete(UUID id, AiModelConfig config) {
        validateExists(id, config);
        if (config.isDefault()) {
            throw AiModelExceptions.cannotDeleteDefaultModel();
        }
    }
}
