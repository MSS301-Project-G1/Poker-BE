package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.response.OrderResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Service quản lý Idempotency bằng Redis (Distributed Lock & Caching).
 * Ngăn chặn double-click và trả về kết quả đơn hàng tức thì trên RAM (< 1ms).
 */
public interface PaymentIdempotencyService {

    /**
     * Thử lấy kết quả OrderResponse đã được cache từ Redis.
     * @param accountId ID người dùng
     * @param targetId ID của Product hoặc CoinBundle
     * @return Optional chứa OrderResponse nếu tìm thấy
     */
    Optional<OrderResponse> getCachedOrder(UUID accountId, UUID targetId);

    /**
     * Thử chiếm khóa phân tán (Distributed Lock) trong Redis để chống Race Condition khi người dùng click đúp cùng mili-giây.
     * @param accountId ID người dùng
     * @param targetId ID của Product hoặc CoinBundle
     * @param lockDuration Thời gian sống tối đa của lock (vd: 10 giây)
     * @return true nếu lấy được lock thành công, false nếu đang có request khác cùng tiến trình xử lý
     */
    boolean acquireLock(UUID accountId, UUID targetId, Duration lockDuration);

    /**
     * Giải phóng khóa phân tán sau khi xử lý xong hoặc gặp lỗi.
     * @param accountId ID người dùng
     * @param targetId ID của Product hoặc CoinBundle
     */
    void releaseLock(UUID accountId, UUID targetId);

    /**
     * Cache kết quả OrderResponse vào Redis với TTL tương ứng thời gian hết hạn đơn hàng.
     * @param accountId ID người dùng
     * @param targetId ID của Product hoặc CoinBundle
     * @param response Đối tượng kết quả đơn hàng
     * @param ttl Thời gian sống của cache
     */
    void cacheOrder(UUID accountId, UUID targetId, OrderResponse response, Duration ttl);

    /**
     * Xóa cache đơn hàng khi đơn đã hoàn tất thanh toán (PAID) hoặc đã bị hủy (CANCELLED).
     * @param accountId ID người dùng
     * @param targetId ID của Product hoặc CoinBundle
     */
    void evictOrder(UUID accountId, UUID targetId);
}
