package com.msspoker.shopservice.repository;

import com.msspoker.shopservice.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    List<InventoryItem> findByAccountId(UUID accountId);

    Optional<InventoryItem> findByAccountIdAndProductId(UUID accountId, UUID productId);

    boolean existsByAccountIdAndProductId(UUID accountId, UUID productId);

    Optional<InventoryItem> findByGrantKey(String grantKey);

    @Query("SELECT i.product.id FROM InventoryItem i WHERE i.accountId = :accountId")
    Set<UUID> findOwnedProductIdsByAccountId(@Param("accountId") UUID accountId);

    @Query("SELECT i FROM InventoryItem i WHERE i.accountId = :accountId " +
           "AND i.product.category.id = :categoryId AND i.isEquipped = true")
    Optional<InventoryItem> findEquippedByAccountIdAndCategoryId(@Param("accountId") UUID accountId,
                                                                 @Param("categoryId") UUID categoryId);
}
