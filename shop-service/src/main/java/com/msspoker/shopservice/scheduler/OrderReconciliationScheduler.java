package com.msspoker.shopservice.scheduler;

import com.msspoker.shopservice.entity.Order;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.enums.OrderFulfillmentStatus;
import com.msspoker.shopservice.repository.OrderRepository;
import com.msspoker.shopservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
     * Chạy định kỳ (mặc định mỗi 1 phút) để đối soát đơn PENDING và đơn PAID chưa giao thưởng:
     * - Sắp xếp các giao dịch có mã đơn số (payosOrderCode) từ thấp đến cao để tránh race condition và deadlock khi khóa dòng.
     * - Kiểm tra trạng thái thanh toán từ PayOS (nếu khách đã chuyển tiền -> đổi PAID và nạp ví).
     * - Đơn quá hạn chỉ đóng khi PayOS xác nhận hết hạn/hủy; lỗi kết nối vẫn giữ PENDING.
     * - Đơn PAID còn chờ giao thưởng được thử lại với cùng idempotency key.
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

        List<Order> awaitingFulfillment = orderRepository.findByStatusAndFulfillmentStatusOrderByPayosOrderCodeAsc(
                OrderStatus.PAID, OrderFulfillmentStatus.PENDING);
        List<Order> sortedOrders = Stream.concat(pendingOrders.stream(), awaitingFulfillment.stream())
                .sorted(Comparator.comparingLong(Order::getPayosOrderCode))
                .toList();

        if (sortedOrders.isEmpty()) return;
        log.info("Tìm thấy {} đơn chờ xác minh hoặc chờ giao thưởng. Tiến hành đối soát theo thứ tự mã đơn...", sortedOrders.size());

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
