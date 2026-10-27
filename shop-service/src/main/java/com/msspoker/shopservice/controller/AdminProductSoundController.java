package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.ProductSoundResponse;
import com.msspoker.shopservice.service.ProductSoundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products/{id}/sound")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Product Sound APIs", description = "Quản lý tệp âm thanh hiệu ứng (soundUrl) cho sản phẩm")
public class AdminProductSoundController {

    private final ProductSoundService productSoundService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên tệp âm thanh cho sản phẩm", description = "Upload file mp3/ogg/wav (tối đa 2MB) và lưu soundUrl vào metadata của sản phẩm")
    public ResponseEntity<ApiResponse<ProductSoundResponse>> uploadProductSound(
            @Parameter(description = "ID sản phẩm cần gắn âm thanh") @PathVariable UUID id,
            @Parameter(description = "Tệp âm thanh (mp3/ogg/wav, tối đa 2MB)") @RequestParam("file") MultipartFile file
    ) {
        ProductSoundResponse response = productSoundService.uploadSound(id, file);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @DeleteMapping
    @Operation(summary = "Gỡ tệp âm thanh khỏi sản phẩm", description = "Xóa soundUrl trong metadata và xóa file trên S3")
    public ResponseEntity<ApiResponse<ProductSoundResponse>> deleteProductSound(
            @Parameter(description = "ID sản phẩm cần gỡ âm thanh") @PathVariable UUID id
    ) {
        ProductSoundResponse response = productSoundService.deleteSound(id);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }
}
