package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.GrantItemResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.OwnedSoundResponse;
import com.msspoker.shopservice.dto.response.OwnershipResponse;
import com.msspoker.shopservice.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalInventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InternalInventoryController controller;

    @Test
    @DisplayName("checkOwnership gọi InventoryService và trả về kết quả sở hữu")
    void checkOwnership() {
        UUID accountId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OwnershipResponse response = new OwnershipResponse(true);

        when(inventoryService.checkOwnership(accountId, productId)).thenReturn(response);

        ResponseEntity<ApiResponse<OwnershipResponse>> result = controller.checkOwnership(accountId, productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        assertThat(result.getBody().getData().owned()).isTrue();
        verify(inventoryService).checkOwnership(accountId, productId);
    }

    @Test
    @DisplayName("getOwnedSound gọi InventoryService và trả về soundUrl")
    void getOwnedSound() {
        UUID accountId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OwnedSoundResponse response = new OwnedSoundResponse(productId, "https://cdn.test/taunt.mp3");

        when(inventoryService.getOwnedSound(accountId, productId)).thenReturn(response);

        ResponseEntity<ApiResponse<OwnedSoundResponse>> result = controller.getOwnedSound(accountId, productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(inventoryService).getOwnedSound(accountId, productId);
    }

    @Test
    @DisplayName("grantItem gọi InventoryService và trả về kết quả cấp phát vật phẩm")
    void grantItem() {
        UUID accountId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        GrantItemRequest request = new GrantItemRequest(accountId, productId, "key-abc");
        GrantItemResponse response = new GrantItemResponse(
                true,
                false,
                InventoryItemResponse.builder().productId(productId).build()
        );

        when(inventoryService.grantItem(request)).thenReturn(response);

        ResponseEntity<ApiResponse<GrantItemResponse>> result = controller.grantItem(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        assertThat(result.getBody().getData().granted()).isTrue();
        verify(inventoryService).grantItem(request);
    }
}
