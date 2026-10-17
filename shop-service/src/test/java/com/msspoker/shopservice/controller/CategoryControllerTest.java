package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.UpdateCategoryRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.CategoryResponse;
import com.msspoker.shopservice.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryController controller;

    @Test
    @DisplayName("getCategories gọi CategoryService và trả về danh sách")
    void getCategories() {
        List<CategoryResponse> list = List.of(new CategoryResponse(UUID.randomUUID(), "FRAME", "Khung"));
        when(categoryService.getCategories()).thenReturn(list);

        ResponseEntity<ApiResponse<List<CategoryResponse>>> response = controller.getCategories();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(list);
        verify(categoryService).getCategories();
    }

    @Test
    @DisplayName("createCategory gọi CategoryService và trả về 201 Created")
    void createCategory() {
        CreateCategoryRequest request = new CreateCategoryRequest("CARD_BACK", "Mặt lưng");
        CategoryResponse responseDto = new CategoryResponse(UUID.randomUUID(), "CARD_BACK", "Mặt lưng");

        when(categoryService.createCategory(request)).thenReturn(responseDto);

        ResponseEntity<ApiResponse<CategoryResponse>> response = controller.createCategory(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(responseDto);
        verify(categoryService).createCategory(request);
    }

    @Test
    @DisplayName("updateCategory gọi CategoryService và trả về 200 OK")
    void updateCategory() {
        UUID id = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("CARD_BACK", "Mặt lưng VIP");
        CategoryResponse responseDto = new CategoryResponse(id, "CARD_BACK", "Mặt lưng VIP");

        when(categoryService.updateCategory(id, request)).thenReturn(responseDto);

        ResponseEntity<ApiResponse<CategoryResponse>> response = controller.updateCategory(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(responseDto);
        verify(categoryService).updateCategory(id, request);
    }

    @Test
    @DisplayName("deleteCategory gọi CategoryService và trả về 200 OK")
    void deleteCategory() {
        UUID id = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = controller.deleteCategory(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(categoryService).deleteCategory(id);
    }
}
