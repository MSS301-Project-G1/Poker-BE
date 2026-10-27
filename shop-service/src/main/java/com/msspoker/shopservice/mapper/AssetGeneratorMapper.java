package com.msspoker.shopservice.mapper;

import com.msspoker.shopservice.dto.response.AssetGenerationBatchResponse;
import com.msspoker.shopservice.entity.AssetGenerationBatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AssetGeneratorMapper {

    @Mapping(target = "batchId", source = "id")
    @Mapping(target = "modelName", expression = "java(batch.getModelConfig() != null ? batch.getModelConfig().getModelName() : \"DEFAULT\")")
    @Mapping(target = "fileCount", constant = "0")
    @Mapping(target = "poseUrls", ignore = true)
    AssetGenerationBatchResponse toResponse(AssetGenerationBatch batch);

    default AssetGenerationBatchResponse toResponse(AssetGenerationBatch batch, int fileCount) {
        return toResponse(batch, fileCount, null);
    }

    default AssetGenerationBatchResponse toResponse(AssetGenerationBatch batch, int fileCount, java.util.Map<String, String> poseUrls) {
        if (batch == null) {
            return null;
        }
        return AssetGenerationBatchResponse.builder()
                .batchId(batch.getId())
                .categoryCode(batch.getCategoryCode())
                .productId(batch.getProductId())
                .modelName(batch.getModelConfig() != null ? batch.getModelConfig().getModelName() : "DEFAULT")
                .s3ZipUrl(batch.getS3ZipUrl())
                .s3PreviewUrl(batch.getS3PreviewUrl())
                .styleSpec(batch.getStyleSpec())
                .status(batch.getStatus())
                .fileCount(fileCount)
                .poseUrls(poseUrls)
                .createdAt(batch.getCreatedAt())
                .expiresAt(batch.getExpiresAt())
                .build();
    }

    List<AssetGenerationBatchResponse> toResponseList(List<AssetGenerationBatch> batches);
}
