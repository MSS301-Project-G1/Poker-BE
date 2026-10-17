package com.msspoker.shopservice.scheduler;

import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.repository.OrderRepository;
import com.msspoker.shopservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReconciliationSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    private OrderReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderReconciliationScheduler(orderRepository, orderService);
    }

    @Test
    @DisplayName("reconcilePendingOrders - Đối soát các đơn hàng PENDING")
    void reconcilePendingOrders_Success() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .payosOrderCode(123456L)
                .build();

        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(order));

        scheduler.reconcilePendingOrders();

        verify(orderService).reconcileOrder(order.getId());
    }

    @Test
    @DisplayName("reconcilePendingOrders - Không có đơn PENDING thì không làm gì")
    void reconcilePendingOrders_Empty() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());

        scheduler.reconcilePendingOrders();

        verify(orderService, never()).reconcileOrder(any());
    }
}
