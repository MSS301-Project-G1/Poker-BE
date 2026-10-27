package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.request.UpdateAiModelConfigRequest;
import com.msspoker.shopservice.entity.AiModelConfig;

import java.util.UUID;

/**
 * Validator interface tập trung toàn bộ các quy tắc nghiệp vụ cho AiModelConfig.
 */
public interface AiModelConfigValidator {

    void validateExists(UUID id, AiModelConfig config);

    void validateForCreate(CreateAiModelConfigRequest request);

    void validateForUpdate(UUID id, UpdateAiModelConfigRequest request, AiModelConfig config);

    void validateForDelete(UUID id, AiModelConfig config);
}
