package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateCategoryRequest(
        @NotBlank(message = "Mã danh mục không được để trống")
        @Size(max = 50, message = "Mã danh mục không được vượt quá 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Mã danh mục chỉ được chứa chữ cái, số và dấu gạch dưới")
        String code,

        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự")
        String name
) {}
