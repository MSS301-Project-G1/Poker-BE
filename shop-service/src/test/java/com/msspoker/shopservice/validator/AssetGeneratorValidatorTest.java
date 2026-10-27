package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.AssetPromptOptions;
import com.msspoker.shopservice.entity.AssetGenerationBatch;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.validator.impl.AssetGeneratorValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetGeneratorValidatorTest {

    private AssetGeneratorValidator validator;
    private UUID batchId;
    private AssetGenerationBatch generatedBatch;

    @BeforeEach
    void setUp() {
        validator = new AssetGeneratorValidatorImpl();
        batchId = UUID.randomUUID();
        generatedBatch = AssetGenerationBatch.builder()
                .id(batchId)
                .categoryCode("CARD_SKIN")
                .status("GENERATED")
                .build();
    }

    @Test
    @DisplayName("validateGenerateRequest: hợp lệ cho CARD_SKIN")
    void validateGenerateRequest_Valid() {
        AssetPromptOptions options = AssetPromptOptions.builder()
                .extraDescription("Mô tả ngắn gọn")
                .avoidElements("chữ mờ")
                .build();
        MockMultipartFile image = new MockMultipartFile("ref", "test.png", "image/png", new byte[]{1, 2, 3});

        assertThatCode(() -> validator.validateGenerateRequest("CARD_SKIN", options, image))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateGenerateRequest: ném ngoại lệ nếu category không hợp lệ")
    void validateGenerateRequest_InvalidCategory() {
        assertThatThrownBy(() -> validator.validateGenerateRequest("UNKNOWN_CAT", null, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Danh mục tài nguyên không hợp lệ");
    }

    @Test
    @DisplayName("validateGenerateRequest: ném ngoại lệ nếu extraDescription > 500 ký tự")
    void validateGenerateRequest_DescriptionTooLong() {
        String longText = "a".repeat(501);
        AssetPromptOptions options = AssetPromptOptions.builder().extraDescription(longText).build();

        assertThatThrownBy(() -> validator.validateGenerateRequest("CARD_SKIN", options, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("vượt quá giới hạn 500 ký tự");
    }

    @Test
    @DisplayName("validateForRefine: ném ngoại lệ nếu batch đã APPLIED")
    void validateForRefine_InvalidStatus() {
        AssetGenerationBatch appliedBatch = AssetGenerationBatch.builder()
                .id(batchId)
                .categoryCode("CARD_SKIN")
                .status("APPLIED")
                .build();

        assertThatThrownBy(() -> validator.validateForRefine(appliedBatch, "Làm đẹp hơn"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Chỉ có thể thao tác trên các batch chưa áp dụng");
    }

    @Test
    @DisplayName("validateForApply: ném ngoại lệ nếu category mismatch")
    void validateForApply_CategoryMismatch() {
        Category tableCat = Category.builder().id(UUID.randomUUID()).code("TABLE").build();
        Product product = Product.builder().id(UUID.randomUUID()).category(tableCat).build();

        assertThatThrownBy(() -> validator.validateForApply(generatedBatch, product.getId(), product))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không khớp với danh mục sản phẩm");
    }
}
