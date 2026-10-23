package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.UpdateCategoryRequest;
import com.msspoker.shopservice.dto.response.CategoryResponse;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.mapper.ProductMapper;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.impl.CategoryServiceImpl;
import com.msspoker.shopservice.validator.CategoryValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);

    private CategoryServiceImpl categoryService;
    private Category category;

    @BeforeEach
    void setUp() {
        CategoryValidator categoryValidator = new com.msspoker.shopservice.validator.impl.CategoryValidatorImpl(categoryRepository, productRepository);
        categoryService = new CategoryServiceImpl(
                categoryRepository,
                productMapper,
                categoryValidator
        );

        category = Category.builder()
                .id(UUID.randomUUID())
                .code("FRAME")
                .name("Khung Avatar")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("getCategories trả về danh sách danh mục sắp xếp theo code")
    void getCategories() {
        when(categoryRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(category));

        List<CategoryResponse> result = categoryService.getCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("FRAME");
        verify(categoryRepository).findAllByOrderByCodeAsc();
    }

    @Test
    @DisplayName("createCategory tạo danh mục mới thành công")
    void createCategorySuccess() {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .code("CARD_BACK")
                .name("Mặt Lưng Bài")
                .build();

        when(categoryRepository.existsByCode("CARD_BACK")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("CARD_BACK");
        assertThat(response.name()).isEqualTo("Mặt Lưng Bài");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("createCategory ném SHOP_CATEGORY_CODE_EXISTS khi code đã tồn tại")
    void createCategoryDuplicateCode() {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .code("FRAME")
                .name("Khung Trùng")
                .build();

        when(categoryRepository.existsByCode("FRAME")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode()).isEqualTo("SHOP_CATEGORY_CODE_EXISTS");
                });
    }

    @Test
    @DisplayName("updateCategory cập nhật cả code và tên danh mục thành công")
    void updateCategorySuccess() {
        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .code("FRAME_VIP")
                .name("Khung Avatar Đẹp")
                .build();

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.existsByCodeAndIdNot("FRAME_VIP", category.getId())).thenReturn(false);

        CategoryResponse response = categoryService.updateCategory(category.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("FRAME_VIP");
        assertThat(response.name()).isEqualTo("Khung Avatar Đẹp");
        assertThat(category.getCode()).isEqualTo("FRAME_VIP");
        assertThat(category.getName()).isEqualTo("Khung Avatar Đẹp");
    }

    @Test
    @DisplayName("updateCategory ném SHOP_CATEGORY_CODE_EXISTS khi code mới bị trùng với danh mục khác")
    void updateCategoryDuplicateCodeThrows() {
        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .code("CARD_BACK")
                .name("Khung Trùng Code")
                .build();

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.existsByCodeAndIdNot("CARD_BACK", category.getId())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(category.getId(), request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode()).isEqualTo("SHOP_CATEGORY_CODE_EXISTS");
                });
    }

    @Test
    @DisplayName("deleteCategory xóa danh mục thành công khi không có sản phẩm")
    void deleteCategorySuccess() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdAndIsDeletedFalse(category.getId())).thenReturn(false);

        categoryService.deleteCategory(category.getId());

        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("deleteCategory ném SHOP_CATEGORY_HAS_PRODUCTS khi danh mục còn sản phẩm")
    void deleteCategoryHasProducts() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdAndIsDeletedFalse(category.getId())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(category.getId()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode()).isEqualTo("SHOP_CATEGORY_HAS_PRODUCTS");
                });
    }
}
