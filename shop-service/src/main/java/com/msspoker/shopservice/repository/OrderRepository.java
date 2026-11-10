package com.msspoker.shopservice.repository;

import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.enums.OrderFulfillmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query(value = "SELECT nextval('orders_payos_order_code_seq')", nativeQuery = true)
    Long generatePayosOrderCode();


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.payosOrderCode = :payosOrderCode")
    Optional<Order> findByPayosOrderCodeForUpdate(@Param("payosOrderCode") long payosOrderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    List<Order> findByStatusAndFulfillmentStatusOrderByPayosOrderCodeAsc(
            OrderStatus status, OrderFulfillmentStatus fulfillmentStatus);

    boolean existsByPayosOrderCode(long payosOrderCode);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findFirstByAccountIdAndProductIdAndStatusOrderByCreatedAtDesc(
            UUID accountId, UUID productId, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Order> findByAccountIdAndProductIdAndStatusOrderByPayosOrderCodeAsc(
            UUID accountId, UUID productId, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findFirstByAccountIdAndBundleIdAndStatusOrderByCreatedAtDesc(
            UUID accountId, UUID bundleId, OrderStatus status);

    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.bundle.id = :bundleId")
    boolean existsByBundleId(@Param("bundleId") UUID bundleId);

    Optional<Order> findFirstByAccountIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID accountId, OrderStatus status, Instant now);

    List<Order> findByAccountIdAndStatusAndExpiresAtAfterOrderByPayosOrderCodeAsc(
            UUID accountId, OrderStatus status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Order> findByAccountIdAndStatusOrderByPayosOrderCodeAsc(UUID accountId, OrderStatus status);

    List<Order> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByStatusOrderByPayosOrderCodeAsc(OrderStatus status);
}
