package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.GrantItemResponse;
import com.msspoker.shopservice.dto.response.OwnedSoundResponse;
import com.msspoker.shopservice.dto.response.OwnershipResponse;
import com.msspoker.shopservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
@Tag(name = "Internal Inventory APIs", description = "API nội bộ giữa các microservices")
public class InternalInventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{accountId}/owns/{productId}")
    @Operation(summary = "Kiểm tra sở hữu vật phẩm", description = "Kiểm tra quyền sở hữu vật phẩm của tài khoản")
    public ResponseEntity<ApiResponse<OwnershipResponse>> checkOwnership(
            @Parameter(description = "UUID tài khoản người dùng")
            @PathVariable UUID accountId,
            @Parameter(description = "UUID sản phẩm")
            @PathVariable UUID productId
    ) {
        OwnershipResponse response = inventoryService.checkOwnership(accountId, productId);
        return ResponseEntity.ok(ApiResponse.success("Kiểm tra quyền sở hữu thành công", response));
    }

    @GetMapping("/{accountId}/sounds/{productId}")
    @Operation(summary = "Lấy âm thanh trêu chọc đã sở hữu", description = "Trả về soundUrl khi tài khoản sở hữu vật phẩm thuộc danh mục SOUND, để game-service phát cho cả bàn")
    public ResponseEntity<ApiResponse<OwnedSoundResponse>> getOwnedSound(
            @Parameter(description = "UUID tài khoản người dùng")
            @PathVariable UUID accountId,
            @Parameter(description = "UUID sản phẩm âm thanh")
            @PathVariable UUID productId
    ) {
        OwnedSoundResponse response = inventoryService.getOwnedSound(accountId, productId);
        return ResponseEntity.ok(ApiResponse.success("Lấy âm thanh thành công", response));
    }

    @PostMapping("/grant")
    @Operation(summary = "Cấp phát vật phẩm", description = "Cấp vật phẩm vào túi đồ người chơi")
    public ResponseEntity<ApiResponse<GrantItemResponse>> grantItem(@Valid @RequestBody GrantItemRequest request) {
        GrantItemResponse response = inventoryService.grantItem(request);
        return ResponseEntity.ok(ApiResponse.success("Xử lý cấp phát vật phẩm thành công", response));
    }
}
