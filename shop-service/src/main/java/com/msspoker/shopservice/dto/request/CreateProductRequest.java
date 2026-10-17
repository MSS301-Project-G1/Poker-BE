package com.msspoker.shopservice.dto.request;

import com.msspoker.shopservice.enums.ProductTier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateProductRequest(
        @NotNull(message = "Danh mục không được để trống")
        UUID categoryId,

        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 150, message = "Tên sản phẩm không được vượt quá 150 ký tự")
        String name,

        String description,

        @NotNull(message = "Hạng sản phẩm không được để trống")
        ProductTier tier,

        @Min(value = 0, message = "Giá sản phẩm không được âm")
        long priceVnd,

        @NotBlank(message = "URL tài nguyên không được để trống")
        @Size(max = 500, message = "URL tài nguyên không được vượt quá 500 ký tự")
        String assetUrl,

        @Size(max = 500, message = "URL video review không được vượt quá 500 ký tự")
        String previewVideoUrl,

        boolean isExclusive
) {}
