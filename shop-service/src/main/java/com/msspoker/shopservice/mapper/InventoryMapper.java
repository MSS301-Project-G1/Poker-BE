package com.msspoker.shopservice.mapper;

import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.dto.response.GrantItemResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.OwnedSoundResponse;
import com.msspoker.shopservice.dto.response.OwnershipResponse;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ItemSource;
import com.msspoker.shopservice.util.AssetUrlSigner;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring", uses = AssetUrlSigner.class)
public interface InventoryMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "categoryCode", source = "product.category.code")
    @Mapping(target = "categoryName", source = "product.category.name")
    @Mapping(target = "assetUrl", source = "product.assetUrl", qualifiedByName = AssetUrlSigner.SIGN_URL)
    @Mapping(target = "previewUrl", source = "product.previewUrl", qualifiedByName = AssetUrlSigner.SIGN_URL)
    @Mapping(target = "previewVideoUrl", source = "product.previewVideoUrl", qualifiedByName = AssetUrlSigner.SIGN_URL)
    @Mapping(target = "metadata", source = "product.metadata", qualifiedByName = AssetUrlSigner.SIGN_METADATA)
    @Mapping(target = "tier", source = "product.tier")
    @Mapping(target = "isEquipped", source = "equipped")
    InventoryItemResponse toInventoryItemResponse(InventoryItem item);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "grantKey", source = "idempotencyKey")
    @Mapping(target = "isEquipped", constant = "false")
    @Mapping(target = "acquiredAt", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "source", ignore = true)
    InventoryItem toInventoryItem(GrantItemRequest request);

    default InventoryItem toInventoryItem(GrantItemRequest request, Product product, ItemSource source) {
        InventoryItem item = toInventoryItem(request);
        if (item != null) {
            item.setProduct(product);
            item.setSource(source);
        }
        return item;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isEquipped", constant = "false")
    @Mapping(target = "acquiredAt", ignore = true)
    InventoryItem toInventoryItem(UUID accountId, Product product, ItemSource source, String grantKey);

    default GrantItemResponse toGrantItemResponse(boolean granted, boolean alreadyOwned, InventoryItemResponse item) {
        return new GrantItemResponse(granted, alreadyOwned, item);
    }

    default OwnershipResponse toOwnershipResponse(boolean owned) {
        return new OwnershipResponse(owned);
    }

    @Mapping(target = "soundUrl", source = "soundUrl", qualifiedByName = AssetUrlSigner.SIGN_URL)
    OwnedSoundResponse toOwnedSoundResponse(UUID productId, String soundUrl);
}
