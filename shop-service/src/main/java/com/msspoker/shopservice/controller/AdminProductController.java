package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.constant.StorageKeyConstants;
import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.ScheduleDeletionRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.CardSkinUploadResponse;
import com.msspoker.shopservice.dto.response.PageResponse;
import com.msspoker.shopservice.dto.response.ProductDeletionResultResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.dto.response.ProductSoundResponse;
import com.msspoker.shopservice.dto.response.UploadedAssetResponse;
import com.msspoker.shopservice.enums.ProductStatus;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.service.CardDeckSkinService;
import com.msspoker.shopservice.service.ProductService;
import com.msspoker.shopservice.service.ProductSoundService;
import com.msspoker.shopservice.service.S3StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Product APIs", description = "Quản lý sản phẩm và skin vật phẩm dành cho Admin")
public class AdminProductController {

    private final ProductService productService;
    private final CardDeckSkinService cardDeckSkinService;
    private final S3StorageService storageService;
    private final ProductSoundService productSoundService;

    public AdminProductController(
            ProductService productService,
            CardDeckSkinService cardDeckSkinService,
            S3StorageService storageService,
            ProductSoundService productSoundService
    ) {
        this.productService = productService;
        this.cardDeckSkinService = cardDeckSkinService;
        this.storageService = storageService;
        this.productSoundService = productSoundService;
    }

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm quản trị", description = "Lấy danh sách tất cả sản phẩm bao gồm cả ACTIVE và MAINTAIN (Bảo Trì)")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAdminProducts(
            @Parameter(description = "Mã danh mục cần lọc")
            @RequestParam(required = false) String category,
            @Parameter(description = "Hạng tier cần lọc")
            @RequestParam(required = false) ProductTier tier,
            @Parameter(description = "Trạng thái sản phẩm (ACTIVE / MAINTAIN)")
            @RequestParam(required = false) ProductStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        PageResponse<ProductResponse> response = productService.getAdminProducts(category, tier, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm quản trị thành công", response));
    }

    @PostMapping(value = "/upload-asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải file tài nguyên / video lên S3", description = "Upload file ảnh preview, video mp4 hoặc sprite lên S3/MinIO và trả về URL")
    public ResponseEntity<ApiResponse<UploadedAssetResponse>> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = StorageKeyConstants.DEFAULT_UPLOAD_FOLDER) String folder
    ) {
        return ResponseEntity.ok(ApiResponse.success("Tải lên file thành công", storageService.uploadAsset(folder, file)));
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

    @PutMapping("/{id}/activate")
    @Operation(summary = "Mở bán lại sản phẩm", description = "Chuyển trạng thái từ MAINTAIN sang ACTIVE để tiếp tục mở bán trên Cửa Hàng")
    public ResponseEntity<ApiResponse<ProductResponse>> activateProduct(
            @Parameter(description = "ID sản phẩm cần mở bán lại") @PathVariable UUID id
    ) {
        ProductResponse response = productService.activateProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Đã mở bán lại sản phẩm thành công", response));
    }

    @PostMapping("/{id}/maintain")
    @Operation(summary = "Đưa sản phẩm về Bảo Trì", description = "Tạm dừng bán trên Shop nhưng bảo lưu 100% kho đồ người chơi đã sở hữu")
    public ResponseEntity<ApiResponse<ProductResponse>> maintainProduct(
            @Parameter(description = "ID sản phẩm cần bảo trì") @PathVariable UUID id
    ) {
        ProductResponse response = productService.maintainProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Chuyển sản phẩm sang trạng thái Bảo Trì thành công", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sản phẩm", description = "Xóa sản phẩm theo ID")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "ID sản phẩm cần xóa") @PathVariable UUID id
    ) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm thành công", null));
    }

    @PostMapping("/{id}/schedule-deletion")
    @Operation(summary = "Lên lịch xóa sản phẩm & thông báo", description = "Đưa sản phẩm đang ở trạng thái Bảo Trì (Maintain) vào chế độ đếm ngược xóa (isDeleting) kèm thông báo toàn server")
    public ResponseEntity<ApiResponse<ProductResponse>> scheduleDeletion(
            @Parameter(description = "ID sản phẩm") @PathVariable UUID id,
            @Valid @RequestBody ScheduleDeletionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID adminId
    ) {
        ProductResponse response = productService.scheduleDeletion(id, request, adminId);
        return ResponseEntity.ok(ApiResponse.success("Lên lịch xóa sản phẩm thành công", response));
    }

    @PostMapping("/{id}/cancel-deletion")
    @Operation(summary = "Hủy lệnh xóa sản phẩm", description = "Hủy quá trình đếm ngược xóa, đưa sản phẩm về trạng thái Bảo Trì (Maintain) bình thường")
    public ResponseEntity<ApiResponse<ProductResponse>> cancelScheduledDeletion(
            @Parameter(description = "ID sản phẩm") @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) UUID adminId
    ) {
        ProductResponse response = productService.cancelScheduledDeletion(id, adminId);
        return ResponseEntity.ok(ApiResponse.success("Đã hủy lệnh xóa sản phẩm thành công", response));
    }

    @PostMapping("/{id}/execute-deletion")
    @Operation(summary = "Thực thi xóa sản phẩm & tự động hoàn tiền", description = "Xóa vĩnh viễn sản phẩm và tự động hoàn trả 100% VIP Coin vào ví của toàn bộ người chơi sở hữu")
    public ResponseEntity<ApiResponse<ProductDeletionResultResponse>> executeDeletion(
            @Parameter(description = "ID sản phẩm") @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) UUID adminId
    ) {
        ProductDeletionResultResponse response = productService.executeProductDeletion(id, adminId);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
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

    @PostMapping(value = "/{id}/sound", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên tệp âm thanh cho sản phẩm", description = "Upload file mp3/ogg/wav (tối đa 2MB) và lưu soundUrl vào metadata của sản phẩm")
    public ResponseEntity<ApiResponse<ProductSoundResponse>> uploadProductSound(
            @Parameter(description = "ID sản phẩm cần gắn âm thanh") @PathVariable UUID id,
            @Parameter(description = "Tệp âm thanh (mp3/ogg/wav, tối đa 2MB)") @RequestParam("file") MultipartFile file
    ) {
        ProductSoundResponse response = productSoundService.uploadSound(id, file);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @DeleteMapping("/{id}/sound")
    @Operation(summary = "Gỡ tệp âm thanh khỏi sản phẩm", description = "Xóa soundUrl trong metadata và xóa file trên S3")
    public ResponseEntity<ApiResponse<ProductSoundResponse>> deleteProductSound(
            @Parameter(description = "ID sản phẩm cần gỡ âm thanh") @PathVariable UUID id
    ) {
        ProductSoundResponse response = productSoundService.deleteSound(id);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }
}
