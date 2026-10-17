package com.msspoker.shopservice.repository;

import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ProductTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id AND p.isDeleted = false")
    Optional<Product> findByIdAndIsDeletedFalse(@Param("id") UUID id);


    boolean existsByCategoryIdAndIsDeletedFalse(UUID categoryId);

    @Query(value = "SELECT p FROM Product p JOIN FETCH p.category WHERE p.isDeleted = false " +
           "AND (:categoryCode IS NULL OR p.category.code = :categoryCode) " +
           "AND (:tier IS NULL OR p.tier = :tier)",
           countQuery = "SELECT count(p) FROM Product p WHERE p.isDeleted = false " +
           "AND (:categoryCode IS NULL OR p.category.code = :categoryCode) " +
           "AND (:tier IS NULL OR p.tier = :tier)")
    Page<Product> findAvailableProducts(@Param("categoryCode") String categoryCode,
                                        @Param("tier") ProductTier tier,
                                        Pageable pageable);
}

