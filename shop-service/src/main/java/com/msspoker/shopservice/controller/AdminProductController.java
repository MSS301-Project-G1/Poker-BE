package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.CardSkinUploadResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.service.CardDeckSkinService;
import com.msspoker.shopservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import com.msspoker.shopservice.service.S3StorageService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Product APIs", description = "Quản lý sản phẩm và skin vật phẩm dành cho Admin")
public class AdminProductController {

    private final ProductService productService;
    private final CardDeckSkinService cardDeckSkinService;
    private final S3StorageService storageService;

    public AdminProductController(
            ProductService productService,
            CardDeckSkinService cardDeckSkinService,
            S3StorageService storageService
    ) {
        this.productService = productService;
        this.cardDeckSkinService = cardDeckSkinService;
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload-asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải file tài nguyên / video lên S3", description = "Upload file ảnh preview, video mp4 hoặc sprite lên S3/MinIO và trả về URL")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "assets") String folder
    ) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectKey = folder + "/" + UUID.randomUUID() + ext;
            String url = storageService.uploadFile(objectKey, file.getBytes(), file.getContentType());
            return ResponseEntity.ok(ApiResponse.success("Tải lên file thành công", Map.of("url", url, "key", objectKey)));
        } catch (Exception e) {
            throw new RuntimeException("Tải lên file thất bại: " + e.getMessage(), e);
        }
    }

    @PostMapping
    @Operation(summary = "Tạo sản phẩm", description = "Tạo sản phẩm mới vào danh mục")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm thành công", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật sản phẩm", description = "Cập nhật thông tin chi tiết sản phẩm")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @Parameter(description = "ID sản phẩm cần sửa") @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sản phẩm", description = "Xóa sản phẩm theo ID")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "ID sản phẩm cần xóa") @PathVariable UUID id
    ) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm thành công", null));
    }

    @PostMapping(value = "/{id}/deck-skin/upload-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Thêm skin qua file ZIP", description = "Tải lên file ZIP chứa 53 lá bài để tạo skin bộ bài tây")
    public ResponseEntity<ApiResponse<CardSkinUploadResponse>> uploadDeckSkinZip(
            @Parameter(description = "UUID sản phẩm cần gắn skin")
            @PathVariable UUID id,
            @Parameter(description = "File ZIP chứa 53 lá bài")
            @RequestParam("file") MultipartFile file
    ) {
        CardSkinUploadResponse response = cardDeckSkinService.uploadDeckSkinZip(id, file);
        return ResponseEntity.ok(ApiResponse.success("Tải lên và xử lý skin thành công", response));
    }

    @PostMapping(value = "/{id}/deck-skin/upload-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Thêm skin qua danh sách ảnh", description = "Tải lên danh sách ảnh lá bài để tạo skin bộ bài tây")
    public ResponseEntity<ApiResponse<CardSkinUploadResponse>> uploadDeckSkinFiles(
            @Parameter(description = "UUID sản phẩm cần gắn skin")
            @PathVariable UUID id,
            @Parameter(description = "Danh sách các file ảnh lá bài")
            @RequestParam("files") List<MultipartFile> files
    ) {
        CardSkinUploadResponse response = cardDeckSkinService.uploadDeckSkinFiles(id, files);
        return ResponseEntity.ok(ApiResponse.success("Tải lên danh sách ảnh skin thành công", response));
    }
}
