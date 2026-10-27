package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.AssetPromptOptions;
import com.msspoker.shopservice.dto.response.AssetGenerationBatchResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.entity.AiPromptPreset;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface AssetGeneratorService {

    AssetGenerationBatchResponse generateAssets(
            String categoryCode,
            AssetPromptOptions options,
            MultipartFile referenceImage,
            MultipartFile soundFile,
            String createdBy
    );

    AssetGenerationBatchResponse refineBatch(UUID batchId, String feedback);

    default ProductResponse applyBatchToProduct(
            UUID batchId,
            UUID targetProductId,
            String productName,
            Long priceVipCoin,
            Long priceVnd
    ) {
        return applyBatchToProduct(batchId, targetProductId, null, productName, null, null, priceVipCoin, priceVnd);
    }

    default ProductResponse applyBatchToProduct(
            UUID batchId,
            UUID targetProductId,
            UUID categoryId,
            String productName,
            Long priceVipCoin,
            Long priceVnd
    ) {
        return applyBatchToProduct(batchId, targetProductId, categoryId, productName, null, null, priceVipCoin, priceVnd);
    }

    ProductResponse applyBatchToProduct(
            UUID batchId,
            UUID targetProductId,
            UUID categoryId,
            String productName,
            String tier,
            String description,
            Long priceVipCoin,
            Long priceVnd
    );

    void deleteBatch(UUID batchId);

    List<AssetGenerationBatchResponse> getRecentBatches();

    String previewPrompt(String categoryCode, AssetPromptOptions options);

    List<AiPromptPreset> getPromptPresets(String categoryCode);

    AiPromptPreset savePromptPreset(AiPromptPreset preset);

    AiPromptPreset updatePromptPreset(UUID id, AiPromptPreset preset);

    void deletePromptPreset(UUID id);
}
