package com.msspoker.shopservice.scheduler;

import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.repository.OrderRepository;
import com.msspoker.shopservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
public class OrderReconciliationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderReconciliationScheduler(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    /**
     * Chạy định kỳ (mặc định mỗi 1 phút) để đối soát toàn bộ các đơn hàng PENDING:
     * - Sắp xếp các giao dịch có mã đơn số (payosOrderCode) từ thấp đến cao để tránh race condition và deadlock khi khóa dòng.
     * - Kiểm tra trạng thái thanh toán từ PayOS (nếu khách đã chuyển tiền -> đổi PAID và nạp ví).
     * - Kiểm tra hạn thanh toán (nếu quá thời gian quy định -> hủy và đóng phiên).
     */
    @Scheduled(
            fixedDelayString = "${shop.order.reconcile-delay-ms:60000}",
            initialDelayString = "${shop.order.reconcile-initial-delay-ms:30000}"
    )
    public void reconcilePendingOrders() {
        List<Order> pendingOrders = orderRepository.findByStatusOrderByPayosOrderCodeAsc(OrderStatus.PENDING);
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);
        }

        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return;
        }

        List<Order> sortedOrders = pendingOrders.stream()
                .sorted(Comparator.comparingLong(Order::getPayosOrderCode))
                .toList();

        log.info("Tìm thấy {} đơn hàng PENDING đang chờ xử lý. Tiến hành đối soát theo thứ tự mã đơn từ thấp đến cao...", sortedOrders.size());

        for (Order order : sortedOrders) {
            try {
                orderService.reconcileOrder(order.getId());
            } catch (Exception ex) {
                log.error("Lỗi khi đối soát đơn hàng id={}, payosOrderCode={}: {}",
                        order.getId(), order.getPayosOrderCode(), ex.getMessage());
            }
        }
    }
}
