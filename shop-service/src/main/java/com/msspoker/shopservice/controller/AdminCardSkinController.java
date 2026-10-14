package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.CardSkinUploadResponse;
import com.msspoker.shopservice.service.CardDeckSkinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products/{id}/deck-skin")
@RequiredArgsConstructor
@Tag(name = "Admin Card Deck Skin APIs", description = "Các API Admin quản lý và tải lên bộ Skin 53 lá bài tây (Texture Atlas & S3 Manifest)")
public class AdminCardSkinController {

    private final CardDeckSkinService cardDeckSkinService;

    @PostMapping(value = "/upload-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Tải 1 file ZIP ",
            description = "Admin tải lên 1 file .zip chứa lá bài . ghép Texture Atlas (Sprite Sheet PNG) và lưu Manifest JSON vào PostgreSQL."
    )
    public ResponseEntity<ApiResponse<CardSkinUploadResponse>> uploadDeckSkinZip(
            @Parameter(description = "UUID sản phẩm cần gắn skin")
            @PathVariable UUID id,
            @Parameter(description = "File ZIP ảnh  (.png, .webp, .jpg)")
            @RequestParam("file") MultipartFile file
    ) {
        CardSkinUploadResponse response = cardDeckSkinService.uploadDeckSkinZip(id, file);
        return ResponseEntity.ok(ApiResponse.success("Tải lên và xử lý thành công", response));
    }

    @PostMapping(value = "/upload-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Tải lên từ danh sách ảnh rời rạc",
            description = "Tải lên danh sách nhiều file ảnh riêng lẻ ghép Texture Atlas."
    )
    public ResponseEntity<ApiResponse<CardSkinUploadResponse>> uploadDeckSkinFiles(
            @Parameter(description = "UUID sản phẩm bài tây cần gắn skin")
            @PathVariable UUID id,
            @Parameter(description = "Danh sách các file ảnh lá bài")
            @RequestParam("files") List<MultipartFile> files
    ) {
        CardSkinUploadResponse response = cardDeckSkinService.uploadDeckSkinFiles(id, files);
        return ResponseEntity.ok(ApiResponse.success("Tải lên danh sách ảnh skin bài tây thành công", response));
    }
}
