package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.AssetPromptOptions;
import com.msspoker.shopservice.entity.AssetGenerationBatch;
import com.msspoker.shopservice.entity.Product;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Validator interface tập trung toàn bộ các quy tắc nghiệp vụ cho Asset Generator.
 */
public interface AssetGeneratorValidator {

    void validateGenerateRequest(String categoryCode, AssetPromptOptions options, MultipartFile referenceImage);

    void validateBatchExists(UUID batchId, AssetGenerationBatch batch);

    void validateForRefine(AssetGenerationBatch batch, String feedback);

    void validateForApply(AssetGenerationBatch batch, UUID productId, Product product);

    void validateForDelete(UUID batchId, AssetGenerationBatch batch);
}
