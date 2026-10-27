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

    ProductResponse applyBatchToProduct(
            UUID batchId,
            UUID targetProductId,
            String productName,
            Long priceVipCoin,
            Long priceVnd
    );

    void deleteBatch(UUID batchId);

    List<AssetGenerationBatchResponse> getRecentBatches();

    String previewPrompt(String categoryCode, AssetPromptOptions options);

    List<AiPromptPreset> getPromptPresets(String categoryCode);
}
