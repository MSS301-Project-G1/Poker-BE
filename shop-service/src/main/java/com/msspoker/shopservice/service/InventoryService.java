package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.dto.response.GrantItemResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.OwnedSoundResponse;
import com.msspoker.shopservice.dto.response.OwnershipResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    List<InventoryItemResponse> getMyInventory(UUID accountId);

    InventoryItemResponse getMyInventoryItem(UUID accountId, UUID productId);

    InventoryItemResponse equipItem(UUID accountId, UUID productId);

    InventoryItemResponse unequipItem(UUID accountId, UUID productId);

    OwnershipResponse checkOwnership(UUID accountId, UUID productId);

    OwnedSoundResponse getOwnedSound(UUID accountId, UUID productId);

    GrantItemResponse grantItem(GrantItemRequest request);
}
