package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.constant.ProductAssetConstants;
import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.dto.response.GrantItemResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.OwnedSoundResponse;
import com.msspoker.shopservice.dto.response.OwnershipResponse;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ItemSource;
import com.msspoker.shopservice.mapper.InventoryMapper;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.InventoryService;
import com.msspoker.shopservice.util.MetadataJsonMerger;
import com.msspoker.shopservice.validator.InventoryValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ProductRepository productRepository;
    private final InventoryMapper inventoryMapper;
    private final InventoryValidator inventoryValidator;
    private final MetadataJsonMerger metadataJsonMerger;

    @Override
    public List<InventoryItemResponse> getMyInventory(UUID accountId) {
        log.debug("Fetching inventory items for accountId={}", accountId);
        return inventoryItemRepository.findByAccountId(accountId).stream()
                .map(inventoryMapper::toInventoryItemResponse)
                .toList();
    }

    @Override
    public InventoryItemResponse getMyInventoryItem(UUID accountId, UUID productId) {
        log.debug("Fetching inventory item detail for accountId={}, productId={}", accountId, productId);
        InventoryItem item = inventoryItemRepository.findByAccountIdAndProductId(accountId, productId).orElse(null);
        inventoryValidator.validateOwned(accountId, productId, item);
        return inventoryMapper.toInventoryItemResponse(item);
    }

    @Override
    @Transactional
    public InventoryItemResponse equipItem(UUID accountId, UUID productId) {
        log.info("Equipping item for accountId={}, productId={}", accountId, productId);
        InventoryItem itemToEquip = inventoryItemRepository.findByAccountIdAndProductId(accountId, productId).orElse(null);

        inventoryValidator.validateOwned(accountId, productId, itemToEquip);

        UUID categoryId = itemToEquip.getProduct().getCategory().getId();

        inventoryItemRepository.findEquippedByAccountIdAndCategoryId(accountId, categoryId)
                .filter(equipped -> !equipped.getId().equals(itemToEquip.getId()))
                .ifPresent(equipped -> {
                    log.info("Unequipping current item {} in category {}", equipped.getId(), categoryId);
                    equipped.setEquipped(false);
                });

        itemToEquip.setEquipped(true);
        log.info("Equipped item {} successfully for accountId={}", itemToEquip.getId(), accountId);
        return inventoryMapper.toInventoryItemResponse(itemToEquip);
    }

    @Override
    @Transactional
    public InventoryItemResponse unequipItem(UUID accountId, UUID productId) {
        log.info("Unequipping item for accountId={}, productId={}", accountId, productId);
        InventoryItem item = inventoryItemRepository.findByAccountIdAndProductId(accountId, productId).orElse(null);

        inventoryValidator.validateOwned(accountId, productId, item);

        item.setEquipped(false);
        log.info("Unequipped item {} successfully for accountId={}", item.getId(), accountId);
        return inventoryMapper.toInventoryItemResponse(item);
    }

    @Override
    public OwnershipResponse checkOwnership(UUID accountId, UUID productId) {
        log.debug("Checking ownership for accountId={}, productId={}", accountId, productId);
        boolean owned = inventoryItemRepository.existsByAccountIdAndProductId(accountId, productId);
        return inventoryMapper.toOwnershipResponse(owned);
    }

    @Override
    public OwnedSoundResponse getOwnedSound(UUID accountId, UUID productId) {
        log.debug("Resolving owned sound for accountId={}, productId={}", accountId, productId);
        InventoryItem item = inventoryItemRepository.findByAccountIdAndProductId(accountId, productId).orElse(null);
        inventoryValidator.validateOwned(accountId, productId, item);

        Product product = item.getProduct();
        String soundUrl = metadataJsonMerger.getStringField(product.getMetadata(), ProductAssetConstants.SOUND_URL_KEY);
        inventoryValidator.validatePlayableSound(product, soundUrl);
        return inventoryMapper.toOwnedSoundResponse(productId, soundUrl);
    }

    @Override
    @Transactional
    public GrantItemResponse grantItem(GrantItemRequest request) {
        log.info("Processing grantItem request: accountId={}, productId={}, idempotencyKey={}",
                request.accountId(), request.productId(), request.idempotencyKey());

        Optional<InventoryItem> existingByKey = inventoryItemRepository.findByGrantKey(request.idempotencyKey());
        if (existingByKey.isPresent()) {
            log.info("Grant request with idempotencyKey={} already fulfilled. Returning existing item.", request.idempotencyKey());
            return inventoryMapper.toGrantItemResponse(
                    true,
                    false,
                    inventoryMapper.toInventoryItemResponse(existingByKey.get())
            );
        }

        Optional<InventoryItem> existingOwned = inventoryItemRepository.findByAccountIdAndProductId(
                request.accountId(), request.productId()
        );
        if (existingOwned.isPresent()) {
            log.info("Account {} already owns productId {}. Grant skipped.", request.accountId(), request.productId());
            return inventoryMapper.toGrantItemResponse(
                    false,
                    true,
                    inventoryMapper.toInventoryItemResponse(existingOwned.get())
            );
        }

        Product product = productRepository.findByIdAndIsDeletedFalse(request.productId()).orElse(null);

        inventoryValidator.validateForGrant(request, product);

        InventoryItem newItem = inventoryMapper.toInventoryItem(request, product, ItemSource.REWARD);
        InventoryItem savedItem = inventoryItemRepository.save(newItem);

        log.info("Granted productId={} to accountId={} successfully with itemId={}",
                request.productId(), request.accountId(), savedItem.getId());

        return inventoryMapper.toGrantItemResponse(
                true,
                false,
                inventoryMapper.toInventoryItemResponse(savedItem)
        );
    }
}
