package com.msspoker.shopservice.repository;

import com.msspoker.shopservice.entity.CoinBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoinBundleRepository extends JpaRepository<CoinBundle, UUID> {

    Optional<CoinBundle> findByIdAndIsActiveTrue(UUID id);

    Optional<CoinBundle> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    List<CoinBundle> findByIsActiveTrueOrderBySortOrderAsc();

    List<CoinBundle> findAllByOrderBySortOrderAsc();
}
