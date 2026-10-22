package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.security.CurrentUser;
import com.msspoker.shopservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
@Tag(name = "Inventory APIs", description = "Túi đồ người chơi")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/me")
    @Operation(summary = "Túi đồ của tôi", description = "Lấy danh sách vật phẩm trong túi đồ của người chơi")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> getMyInventory(
            @CurrentUser UUID accountId
    ) {
        List<InventoryItemResponse> items = inventoryService.getMyInventory(accountId);
        return ResponseEntity.ok(ApiResponse.success("Lấy túi đồ thành công", items));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Chi tiết vật phẩm", description = "Lấy thông tin chi tiết vật phẩm trong túi đồ")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getMyInventoryItem(
            @CurrentUser UUID accountId,
            @Parameter(description = "ID của sản phẩm trong túi đồ")
            @PathVariable UUID productId
    ) {
        InventoryItemResponse response = inventoryService.getMyInventoryItem(accountId, productId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết vật phẩm thành công", response));
    }

    @PutMapping("/{productId}/equip")
    @Operation(summary = "Trang bị vật phẩm", description = "Trang bị vật phẩm vào ô sử dụng")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> equipItem(
            @CurrentUser UUID accountId,
            @Parameter(description = "ID của sản phẩm cần trang bị")
            @PathVariable UUID productId
    ) {
        InventoryItemResponse response = inventoryService.equipItem(accountId, productId);
        return ResponseEntity.ok(ApiResponse.success("Trang bị vật phẩm thành công", response));
    }

    @DeleteMapping("/{productId}/equip")
    @Operation(summary = "Tháo trang bị", description = "Tháo vật phẩm đang trang bị")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> unequipItem(
            @CurrentUser UUID accountId,
            @Parameter(description = "ID của sản phẩm cần tháo trang bị")
            @PathVariable UUID productId
    ) {
        InventoryItemResponse response = inventoryService.unequipItem(accountId, productId);
        return ResponseEntity.ok(ApiResponse.success("Tháo trang bị vật phẩm thành công", response));
    }
}
