package com.msspoker.shopservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.msspoker.shopservice.dto.response.OrderResponse;
import com.msspoker.shopservice.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private PaymentIdempotencyServiceImpl idempotencyService;

    private UUID accountId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        idempotencyService = new PaymentIdempotencyServiceImpl(stringRedisTemplate, objectMapper);

        accountId = UUID.randomUUID();
        targetId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getCachedOrder - Thành công khi có dữ liệu trong Redis")
    void getCachedOrder_Hit() throws Exception {
        OrderResponse cached = new OrderResponse(
                UUID.randomUUID(),
                accountId,
                targetId,
                "VIP Card",
                null,
                null,
                null,
                50000L,
                123456L,
                "https://checkout.url",
                "qr-data",
                "qr-image",
                OrderStatus.PENDING,
                null,
                Instant.now().plusSeconds(600),
                Instant.now()
        );

        String json = objectMapper.writeValueAsString(cached);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:result:order:" + accountId + ":" + targetId)).thenReturn(json);

        Optional<OrderResponse> result = idempotencyService.getCachedOrder(accountId, targetId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(cached.id());
        assertThat(result.get().payosOrderCode()).isEqualTo(123456L);
    }

    @Test
    @DisplayName("getCachedOrder - Trả về empty khi không có trong Redis")
    void getCachedOrder_Miss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:result:order:" + accountId + ":" + targetId)).thenReturn(null);

        Optional<OrderResponse> result = idempotencyService.getCachedOrder(accountId, targetId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCachedOrder - An toàn fallback khi Redis ném lỗi")
    void getCachedOrder_Exception() {
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        Optional<OrderResponse> result = idempotencyService.getCachedOrder(accountId, targetId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("acquireLock - Thành công chiếm lock")
    void acquireLock_Success() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idempotency:lock:order:" + accountId + ":" + targetId), eq("LOCKED"), any(Duration.class)))
                .thenReturn(true);

        boolean acquired = idempotencyService.acquireLock(accountId, targetId, Duration.ofSeconds(10));

        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("acquireLock - Thất bại khi lock đã bị chiếm")
    void acquireLock_Failure() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idempotency:lock:order:" + accountId + ":" + targetId), eq("LOCKED"), any(Duration.class)))
                .thenReturn(false);

        boolean acquired = idempotencyService.acquireLock(accountId, targetId, Duration.ofSeconds(10));

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("acquireLock - Graceful degradation trả về true khi Redis lỗi")
    void acquireLock_Exception() {
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis timeout"));

        boolean acquired = idempotencyService.acquireLock(accountId, targetId, Duration.ofSeconds(10));

        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("releaseLock - Xóa lock key thành công")
    void releaseLock_Success() {
        idempotencyService.releaseLock(accountId, targetId);

        verify(stringRedisTemplate).delete("idempotency:lock:order:" + accountId + ":" + targetId);
    }

    @Test
    @DisplayName("cacheOrder - Lưu JSON kết quả vào Redis với TTL")
    void cacheOrder_Success() {
        OrderResponse response = new OrderResponse(
                UUID.randomUUID(),
                accountId,
                targetId,
                "Card",
                null,
                null,
                null,
                20000L,
                111222L,
                "https://checkout",
                "qr",
                "qr-img",
                OrderStatus.PENDING,
                null,
                Instant.now().plusSeconds(900),
                Instant.now()
        );

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        idempotencyService.cacheOrder(accountId, targetId, response, Duration.ofMinutes(15));

        verify(valueOperations).set(eq("idempotency:result:order:" + accountId + ":" + targetId), any(String.class), eq(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("evictOrder - Xóa cache kết quả đơn hàng khỏi Redis")
    void evictOrder_Success() {
        idempotencyService.evictOrder(accountId, targetId);

        verify(stringRedisTemplate).delete("idempotency:result:order:" + accountId + ":" + targetId);
    }
}
