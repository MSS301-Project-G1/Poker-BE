package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.UpdateCategoryRequest;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.validator.impl.CategoryValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryValidatorTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryValidatorImpl categoryValidator;

    private UUID categoryId;
    private Category category;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        category = Category.builder()
                .id(categoryId)
                .code("FRAME")
                .name("Khung Avatar")
                .build();
    }

    @Test
    @DisplayName("validateExists: thành công khi category tồn tại")
    void validateExists_Success() {
        assertThatCode(() -> categoryValidator.validateExists(categoryId, category))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateExists: thất bại khi category null")
    void validateExists_Throws() {
        assertThatThrownBy(() -> categoryValidator.validateExists(categoryId, null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validateForCreate: ném lỗi khi code đã tồn tại")
    void validateForCreate_DuplicateCodeThrows() {
        CreateCategoryRequest request = new CreateCategoryRequest("FRAME", "Khung Avatar");
        when(categoryRepository.existsByCode("FRAME")).thenReturn(true);

        assertThatThrownBy(() -> categoryValidator.validateForCreate(request))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validateForDelete: ném lỗi khi danh mục vẫn còn sản phẩm")
    void validateForDelete_HasProductsThrows() {
        when(productRepository.existsByCategoryIdAndIsDeletedFalse(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> categoryValidator.validateForDelete(categoryId, category))
                .isInstanceOf(AppException.class);
    }
}
