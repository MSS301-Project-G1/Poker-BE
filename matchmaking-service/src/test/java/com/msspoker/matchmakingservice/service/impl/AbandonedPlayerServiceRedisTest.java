package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.service.store.PartyStore;
import com.msspoker.matchmakingservice.service.impl.store.RedisPartyStore;
import com.msspoker.matchmakingservice.service.impl.store.RedisQueueStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/** Chạy trên Redis thật để kiểm tra các script Lua của hàng chờ và party phối hợp đúng khi dọn người bỏ đi. */
class AbandonedPlayerServiceRedisTest {
    private static final String PASSWORD = "123456";
    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisQueueStore queueStore;
    private static RedisPartyStore partyStore;
    private static AbandonedPlayerServiceImpl service;

    @BeforeAll
    static void startRedis() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the Redis cleanup test");
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        queueStore = new RedisQueueStore(template, new MatchmakingProperties());
        partyStore = new RedisPartyStore(template);
        service = new AbandonedPlayerServiceImpl(queueStore, partyStore, mock(MatchProposalPlayerRepository.class));
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        if (redis != null) redis.stop();
    }

    @Test
    void memberWhoClosedTheTabStopsTheSearchingPartyAndLeavesIt() {
        UUID partyId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        assertThat(partyStore.create(partyId, "AB12CD34", PASSWORD, leader, Instant.EPOCH)).isEqualTo(PartyStore.CreateResult.CREATED);
        assertThat(partyStore.join(member, "AB12CD34", PASSWORD, 6, Instant.EPOCH)).isEqualTo(PartyStore.JoinResult.JOINED);
        assertThat(queueStore.enqueueParty(List.of(
                new QueueEntry(leader, MatchMode.NORMAL, Instant.EPOCH, partyId),
                new QueueEntry(member, MatchMode.NORMAL, Instant.EPOCH, partyId)))).isTrue();

        assertThat(service.release(member)).isTrue();

        assertThat(queueStore.find(leader)).isEmpty();
        assertThat(queueStore.find(member)).isEmpty();
        assertThat(queueStore.size(MatchMode.NORMAL)).isZero();
        assertThat(partyStore.findByAccountId(member)).isEmpty();
        assertThat(partyStore.findByAccountId(leader).orElseThrow().memberIds()).containsExactly(leader);
    }

    @Test
    void leaderWhoClosedTheTabDisbandsTheParty() {
        UUID partyId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        assertThat(partyStore.create(partyId, "EF56GH78", PASSWORD, leader, Instant.EPOCH)).isEqualTo(PartyStore.CreateResult.CREATED);
        assertThat(partyStore.join(member, "EF56GH78", PASSWORD, 6, Instant.EPOCH)).isEqualTo(PartyStore.JoinResult.JOINED);

        assertThat(service.release(leader)).isTrue();

        assertThat(partyStore.findByAccountId(leader)).isEmpty();
        assertThat(partyStore.findByAccountId(member)).isEmpty();
    }

    @Test
    void soloPlayerWhoClosedTheTabLeavesTheRankQueue() {
        UUID player = UUID.randomUUID();
        assertThat(queueStore.enqueue(QueueEntry.rank(player, Instant.EPOCH, 1000, 0, 0))).isTrue();

        assertThat(service.release(player)).isTrue();

        assertThat(queueStore.find(player)).isEmpty();
        assertThat(queueStore.position(player, MatchMode.RANK)).isEmpty();
    }

    @Test
    void playerAlreadyReservedForAMatchIsKeptForTheProposalFlow() {
        UUID player = UUID.randomUUID();
        QueueEntry entry = new QueueEntry(player, MatchMode.NORMAL, Instant.EPOCH);
        assertThat(queueStore.enqueue(entry)).isTrue();
        assertThat(queueStore.reserve(MatchMode.NORMAL, List.of(entry), UUID.randomUUID())).isTrue();

        assertThat(service.release(player)).isFalse();

        assertThat(queueStore.find(player)).isPresent();
        assertThat(queueStore.reservation(player)).isPresent();
    }
}
