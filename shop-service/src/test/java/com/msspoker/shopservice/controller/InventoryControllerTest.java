package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.service.InventoryService;
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
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController controller;

    @Test
    @DisplayName("getMyInventory ủy quyền cho InventoryService và trả về ApiResponse danh sách túi đồ")
    void getMyInventory() {
        UUID accountId = UUID.randomUUID();
        List<InventoryItemResponse> items = List.of();

        when(inventoryService.getMyInventory(accountId)).thenReturn(items);

        ResponseEntity<ApiResponse<List<InventoryItemResponse>>> result = controller.getMyInventory(accountId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo("SHOP_SUCCESS");
        assertThat(result.getBody().getData()).isEqualTo(items);
        verify(inventoryService).getMyInventory(accountId);
    }

    @Test
    @DisplayName("getMyInventoryItem gọi InventoryService và trả về ApiResponse chi tiết vật phẩm")
    void getMyInventoryItem() {
        UUID accountId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        InventoryItemResponse item = InventoryItemResponse.builder()
                .productId(productId)
                .build();

        when(inventoryService.getMyInventoryItem(accountId, productId)).thenReturn(item);

        ResponseEntity<ApiResponse<InventoryItemResponse>> result = controller.getMyInventoryItem(accountId, productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(item);
        verify(inventoryService).getMyInventoryItem(accountId, productId);
    }

    @Test
    @DisplayName("equipItem ủy quyền cho InventoryService và trả về ApiResponse của vật phẩm đã trang bị")
    void equipItem() {
        UUID accountId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        InventoryItemResponse response = InventoryItemResponse.builder()
                .productId(productId)
                .isEquipped(true)
                .build();

        when(inventoryService.equipItem(accountId, productId)).thenReturn(response);

        ResponseEntity<ApiResponse<InventoryItemResponse>> result = controller.equipItem(accountId, productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(inventoryService).equipItem(accountId, productId);
    }

    @Test
    @DisplayName("unequipItem ủy quyền cho InventoryService và trả về ApiResponse của vật phẩm đã gỡ trang bị")
    void unequipItem() {
        UUID accountId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        InventoryItemResponse response = InventoryItemResponse.builder()
                .productId(productId)
                .isEquipped(false)
                .build();

        when(inventoryService.unequipItem(accountId, productId)).thenReturn(response);

        ResponseEntity<ApiResponse<InventoryItemResponse>> result = controller.unequipItem(accountId, productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(inventoryService).unequipItem(accountId, productId);
    }
}
