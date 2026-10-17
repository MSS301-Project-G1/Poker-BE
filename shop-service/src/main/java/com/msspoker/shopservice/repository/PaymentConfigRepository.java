package com.msspoker.shopservice.repository;

import com.msspoker.shopservice.entity.PaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentConfigRepository extends JpaRepository<PaymentConfig, UUID> {

    Optional<PaymentConfig> findByActiveTrue();

    List<PaymentConfig> findAllByOrderByCreatedAtAsc();

    @Modifying
    @Query("UPDATE PaymentConfig p SET p.active = false")
    void deactivateAll();
}
