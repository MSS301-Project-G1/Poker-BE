package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.dto.request.AssetPromptOptions;
import com.msspoker.shopservice.entity.AssetGenerationBatch;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.AssetGeneratorExceptions;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.validator.AssetGeneratorValidator;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Component
public class AssetGeneratorValidatorImpl implements AssetGeneratorValidator {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of("CARD_SKIN", "DEALER", "TABLE");
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    public void validateGenerateRequest(String categoryCode, AssetPromptOptions options, MultipartFile referenceImage) {
        if (categoryCode == null || !ALLOWED_CATEGORIES.contains(categoryCode.toUpperCase())) {
            throw AssetGeneratorExceptions.invalidInput("Danh mục tài nguyên không hợp lệ: " + categoryCode + ". Hỗ trợ: " + ALLOWED_CATEGORIES);
        }

        if (options != null) {
            if (options.extraDescription() != null && options.extraDescription().length() > 500) {
                throw AssetGeneratorExceptions.invalidInput("Mô tả bổ sung vượt quá giới hạn 500 ký tự (hiện tại: " + options.extraDescription().length() + ")");
            }
            if (options.avoidElements() != null && options.avoidElements().length() > 200) {
                throw AssetGeneratorExceptions.invalidInput("Phần tử né tránh vượt quá giới hạn 200 ký tự (hiện tại: " + options.avoidElements().length() + ")");
            }
        }

        if (referenceImage != null && !referenceImage.isEmpty()) {
            if (referenceImage.getSize() > MAX_IMAGE_SIZE) {
                throw AssetGeneratorExceptions.invalidInput("Dung lượng ảnh tham chiếu vượt quá giới hạn 10MB");
            }
            String contentType = referenceImage.getContentType();
            if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/octet-stream"))) {
                throw AssetGeneratorExceptions.invalidInput("Định dạng file tải lên không phải là ảnh hợp lệ (" + contentType + ")");
            }
        }
    }

    @Override
    public void validateBatchExists(UUID batchId, AssetGenerationBatch batch) {
        if (batch == null) {
            throw AssetGeneratorExceptions.batchNotFound(batchId);
        }
    }

    @Override
    public void validateForRefine(AssetGenerationBatch batch, String feedback) {
        if (batch == null) {
            throw AssetGeneratorExceptions.batchNotFound(null);
        }
        if (!"GENERATED".equalsIgnoreCase(batch.getStatus())) {
            throw AssetGeneratorExceptions.invalidBatchStatus(batch.getStatus());
        }
        if (feedback == null || feedback.trim().isEmpty()) {
            throw AssetGeneratorExceptions.invalidInput("Ý kiến phản hồi tinh chỉnh không được để trống.");
        }
    }

    @Override
    public void validateForApply(AssetGenerationBatch batch, UUID productId, Product product) {
        if (batch == null) {
            throw AssetGeneratorExceptions.batchNotFound(null);
        }
        if (productId != null && product == null) {
            throw ProductExceptions.productNotFound();
        }
        if (product != null && product.getCategory() != null) {
            String prodCat = product.getCategory().getCode();
            if (!batch.getCategoryCode().equalsIgnoreCase(prodCat)) {
                throw AssetGeneratorExceptions.categoryMismatch(batch.getCategoryCode(), prodCat);
            }
        }
    }

    @Override
    public void validateForDelete(UUID batchId, AssetGenerationBatch batch) {
        validateBatchExists(batchId, batch);
    }
}
