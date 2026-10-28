package com.msspoker.shopservice.scheduler;

import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.repository.OrderRepository;
import com.msspoker.shopservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    @DisplayName("reconcilePendingOrders - Đối soát các đơn hàng PENDING sắp xếp mã đơn từ thấp đến cao")
    void reconcilePendingOrders_Success() {
        Order orderHigh = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .payosOrderCode(300000L)
                .build();
        Order orderLow = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .payosOrderCode(100000L)
                .build();
        Order orderMid = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .payosOrderCode(200000L)
                .build();

        when(orderRepository.findByStatusOrderByPayosOrderCodeAsc(OrderStatus.PENDING))
                .thenReturn(List.of(orderHigh, orderLow, orderMid));

        scheduler.reconcilePendingOrders();

        InOrder inOrder = Mockito.inOrder(orderService);
        inOrder.verify(orderService).reconcileOrder(orderLow.getId());
        inOrder.verify(orderService).reconcileOrder(orderMid.getId());
        inOrder.verify(orderService).reconcileOrder(orderHigh.getId());
    }

    @Test
    @DisplayName("reconcilePendingOrders - Không có đơn PENDING thì không làm gì")
    void reconcilePendingOrders_Empty() {
        when(orderRepository.findByStatusOrderByPayosOrderCodeAsc(OrderStatus.PENDING)).thenReturn(List.of());
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());

        scheduler.reconcilePendingOrders();

        verify(orderService, never()).reconcileOrder(any());
    }
}
