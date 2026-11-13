package com.msspoker.matchmakingservice.service.impl.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

class RedisPresenceStoreTest {
    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisPresenceStore store;

    @BeforeAll
    static void startRedis() {
        assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for the Redis presence test");
        redis =
                new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);
        redis.start();
        connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        store = new RedisPresenceStore(template);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        if (redis != null) redis.stop();
    }

    @Test
    void listsSilentPlayersOldestFirstUntilTheyComeBackOrAreForgotten() {
        Instant cutoff = Instant.parse("2026-11-11T10:00:00Z");
        UUID longGone = UUID.randomUUID();
        UUID justGone = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID returning = UUID.randomUUID();
        store.touch(justGone, cutoff.minusSeconds(5));
        store.touch(longGone, cutoff.minusSeconds(300));
        store.touch(active, cutoff.plusSeconds(1));
        store.touch(returning, cutoff.minusSeconds(120));

        store.touch(returning, cutoff.plusSeconds(2));
        assertThat(store.seenBefore(cutoff, 10)).containsExactly(longGone, justGone);
        assertThat(store.seenBefore(cutoff, 1)).containsExactly(longGone);

        store.forget(longGone);
        assertThat(store.seenBefore(cutoff, 10)).containsExactly(justGone);
    }
}
