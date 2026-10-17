package com.msspoker.shopservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.dto.response.OrderResponse;
import com.msspoker.shopservice.service.PaymentIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIdempotencyServiceImpl implements PaymentIdempotencyService {

    private static final String LOCK_PREFIX = "idempotency:lock:order:";
    private static final String RESULT_PREFIX = "idempotency:result:order:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<OrderResponse> getCachedOrder(UUID accountId, UUID targetId) {
        String key = RESULT_PREFIX + accountId + ":" + targetId;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                OrderResponse response = objectMapper.readValue(json, OrderResponse.class);
                log.info("Redis Idempotency HIT cho key={}. Trả về OrderResponse từ Redis cache.", key);
                return Optional.of(response);
            }
        } catch (Exception ex) {
            log.warn("Lỗi khi đọc Redis Idempotency cho key={}: {}. Bỏ qua fallback DB.", key, ex.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public boolean acquireLock(UUID accountId, UUID targetId, Duration lockDuration) {
        String key = LOCK_PREFIX + accountId + ":" + targetId;
        try {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "LOCKED", lockDuration);
            return Boolean.TRUE.equals(success);
        } catch (Exception ex) {
            log.warn("Lỗi khi acquire Redis lock cho key={}: {}. Fallback an toàn cho phép tiếp tục.", key, ex.getMessage());
            return true;
        }
    }

    @Override
    public void releaseLock(UUID accountId, UUID targetId) {
        String key = LOCK_PREFIX + accountId + ":" + targetId;
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Lỗi khi release Redis lock cho key={}: {}", key, ex.getMessage());
        }
    }

    @Override
    public void cacheOrder(UUID accountId, UUID targetId, OrderResponse response, Duration ttl) {
        String key = RESULT_PREFIX + accountId + ":" + targetId;
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(key, json, ttl);
            log.info("Đã cache OrderResponse vào Redis cho key={}, ttl={}", key, ttl);
        } catch (Exception ex) {
            log.warn("Lỗi khi cache OrderResponse vào Redis cho key={}: {}", key, ex.getMessage());
        }
    }

    @Override
    public void evictOrder(UUID accountId, UUID targetId) {
        String key = RESULT_PREFIX + accountId + ":" + targetId;
        try {
            stringRedisTemplate.delete(key);
            log.info("Đã xóa cache Redis Idempotency cho key={}", key);
        } catch (Exception ex) {
            log.warn("Lỗi khi evict Redis Idempotency cho key={}: {}", key, ex.getMessage());
        }
    }
}
