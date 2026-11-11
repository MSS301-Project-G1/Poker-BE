package com.msspoker.matchmakingservice.service.store.redis;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.service.store.PresenceStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Một sorted set: member là accountId, score là thời điểm thấy gần nhất (epoch millis). */
@Repository
public class RedisPresenceStore implements PresenceStore {
    private static final String KEY = MatchmakingConstants.REDIS_PREFIX + "presence";

    private final StringRedisTemplate redis;

    public RedisPresenceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void touch(UUID accountId, Instant seenAt) {
        redis.opsForZSet().add(KEY, accountId.toString(), seenAt.toEpochMilli());
    }

    @Override
    public List<UUID> seenBefore(Instant cutoff, int limit) {
        Set<String> ids = redis.opsForZSet().rangeByScore(KEY, Double.NEGATIVE_INFINITY, cutoff.toEpochMilli(), 0, limit);
        return ids == null ? List.of() : ids.stream().map(UUID::fromString).toList();
    }

    @Override
    public void forget(UUID accountId) {
        redis.opsForZSet().remove(KEY, accountId.toString());
    }
}
