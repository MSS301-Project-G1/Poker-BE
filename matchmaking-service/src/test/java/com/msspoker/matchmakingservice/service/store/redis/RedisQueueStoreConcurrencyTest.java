package com.msspoker.matchmakingservice.service.store.redis;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.model.InGameSession;
import com.msspoker.matchmakingservice.service.store.QueueStore;
import com.msspoker.matchmakingservice.service.store.PartyStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedisQueueStoreConcurrencyTest {
    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisQueueStore store;
    private static RedisPartyStore partyStore;
    private static final String PASSWORD = "123456";

    @BeforeAll
    static void startRedis() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for the Redis concurrency test");

        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();

        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        store = new RedisQueueStore(template, new MatchmakingProperties());
        partyStore = new RedisPartyStore(template);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        if (redis != null) redis.stop();
    }

    @Test
    void onlyOneWorkerCanReserveTheSameAccounts() throws Exception {
        List<QueueEntry> entries = List.of(
                new QueueEntry(UUID.randomUUID(), MatchMode.NORMAL, Instant.EPOCH.plusSeconds(1)),
                new QueueEntry(UUID.randomUUID(), MatchMode.NORMAL, Instant.EPOCH.plusSeconds(2))
        );
        entries.forEach(entry -> assertThat(store.enqueue(entry)).isTrue());

        UUID firstProposal = UUID.randomUUID();
        UUID secondProposal = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = workers.submit(() -> reserveTogether(entries, firstProposal, ready, start));
            Future<Boolean> second = workers.submit(() -> reserveTogether(entries, secondProposal, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);

            UUID winningProposal = store.reservation(entries.getFirst().accountId()).orElseThrow();
            assertThat(winningProposal).isIn(firstProposal, secondProposal);
            assertThat(store.reservation(entries.getLast().accountId())).contains(winningProposal);
            assertThat(store.oldest(MatchMode.NORMAL, 2)).isEmpty();
        } finally {
            workers.shutdownNow();
            InGameSession session = new InGameSession(
                    MatchMode.NORMAL,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "localhost",
                    8082,
                    "/ws/game"
            );
            store.complete(entries, session);
            assertThat(store.inGame(entries.getFirst().accountId())).contains(session);
            assertThat(store.inGame(entries.getLast().accountId())).contains(session);
            entries.forEach(entry -> store.clearInGame(entry.accountId()));
        }
    }

    @Test
    void requeueAtomicallyRestoresPayloadOrderAndRemovesReservations() {
        List<QueueEntry> entries = List.of(
                new QueueEntry(UUID.randomUUID(), MatchMode.NORMAL, Instant.EPOCH.plusSeconds(11)),
                new QueueEntry(UUID.randomUUID(), MatchMode.NORMAL, Instant.EPOCH.plusSeconds(12))
        );
        entries.forEach(entry -> assertThat(store.enqueue(entry)).isTrue());
        assertThat(store.reserve(MatchMode.NORMAL, entries, UUID.randomUUID())).isTrue();

        store.requeue(entries);

        assertThat(store.oldest(MatchMode.NORMAL, 2)).containsExactlyElementsOf(entries);
        entries.forEach(entry -> {
            assertThat(store.find(entry.accountId())).contains(entry);
            assertThat(store.reservation(entry.accountId())).isEmpty();
        });
        entries.forEach(entry -> store.cancel(entry.accountId(), MatchMode.NORMAL));
    }

    @Test
    void rankSnapshotSurvivesRedisRoundTripAndRequeue() {
        QueueEntry entry = QueueEntry.rank(
                UUID.randomUUID(),
                Instant.EPOCH.plusSeconds(20),
                0,
                4,
                1
        );
        assertThat(store.enqueue(entry)).isTrue();
        assertThat(store.find(entry.accountId())).contains(entry);
        assertThat(store.oldest(MatchMode.RANK, 1)).containsExactly(entry);

        assertThat(store.reserve(MatchMode.RANK, List.of(entry), UUID.randomUUID())).isTrue();
        store.requeue(List.of(entry));

        assertThat(store.find(entry.accountId())).contains(entry);
        assertThat(store.reservation(entry.accountId())).isEmpty();
        assertThat(store.cancel(entry.accountId(), MatchMode.RANK))
                .isEqualTo(QueueStore.CancelResult.CANCELLED);
    }

    @Test
    void discardAtomicallyRemovesRankPayloadAndReservation() {
        QueueEntry entry = QueueEntry.rank(UUID.randomUUID(), Instant.now(), 1000, 1, 0);
        assertThat(store.enqueue(entry)).isTrue();
        assertThat(store.reserve(MatchMode.RANK, List.of(entry), UUID.randomUUID())).isTrue();

        store.discard(List.of(entry));

        assertThat(store.find(entry.accountId())).isEmpty();
        assertThat(store.reservation(entry.accountId())).isEmpty();
        assertThat(store.oldest(MatchMode.RANK, 1)).isEmpty();
    }

    @Test
    void rankCooldownStoresServerDeadline() {
        UUID accountId = UUID.randomUUID();
        Instant deadline = Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.MILLIS);

        store.startRankCooldown(accountId, deadline);

        assertThat(store.rankCooldown(accountId)).contains(deadline);
    }

    @Test
    void cancelRacingReserveAlwaysEndsInOneValidState() throws Exception {
        QueueEntry entry = new QueueEntry(
                UUID.randomUUID(),
                MatchMode.NORMAL,
                Instant.now().truncatedTo(ChronoUnit.MILLIS)
        );
        assertThat(store.enqueue(entry)).isTrue();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> reserve = workers.submit(() -> {
                ready.countDown();
                start.await();
                return store.reserve(MatchMode.NORMAL, List.of(entry), UUID.randomUUID());
            });
            Future<QueueStore.CancelResult> cancel = workers.submit(() -> {
                ready.countDown();
                start.await();
                return store.cancel(entry.accountId(), MatchMode.NORMAL);
            });
            ready.await();
            start.countDown();

            boolean reserved = reserve.get();
            QueueStore.CancelResult cancelled = cancel.get();
            if (reserved) {
                assertThat(cancelled).isEqualTo(QueueStore.CancelResult.RESERVED);
                assertThat(store.reservation(entry.accountId())).isPresent();
                assertThat(store.find(entry.accountId())).contains(entry);
                store.requeue(List.of(entry));
                store.cancel(entry.accountId(), MatchMode.NORMAL);
            } else {
                assertThat(cancelled).isEqualTo(QueueStore.CancelResult.CANCELLED);
                assertThat(store.reservation(entry.accountId())).isEmpty();
                assertThat(store.find(entry.accountId())).isEmpty();
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void leaderLeavingAtomicallyDisbandsPartyAndReleasesEveryMember() {
        UUID leader = UUID.randomUUID();
        UUID firstMember = UUID.randomUUID();
        UUID secondMember = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        String partyCode = "AB12CD34";

        assertThat(partyStore.create(partyId, partyCode, PASSWORD, leader, Instant.EPOCH))
                .isEqualTo(PartyStore.CreateResult.CREATED);
        assertThat(partyStore.join(firstMember, partyCode, "000000", 6, Instant.EPOCH.plusSeconds(1)))
                .isEqualTo(PartyStore.JoinResult.WRONG_PASSWORD);
        assertThat(partyStore.findByAccountId(firstMember)).isEmpty();
        assertThat(partyStore.join(firstMember, partyCode, PASSWORD, 6, Instant.EPOCH.plusSeconds(1)))
                .isEqualTo(PartyStore.JoinResult.JOINED);
        assertThat(partyStore.join(secondMember, partyCode, PASSWORD, 6, Instant.EPOCH.plusSeconds(2)))
                .isEqualTo(PartyStore.JoinResult.JOINED);

        assertThat(partyStore.leave(firstMember)).isEqualTo(PartyStore.LeaveResult.LEFT);
        assertThat(partyStore.findByAccountId(leader).orElseThrow().leaderId()).isEqualTo(leader);
        assertThat(partyStore.findByAccountId(secondMember).orElseThrow().memberIds()).containsExactly(leader, secondMember);
        assertThat(partyStore.join(firstMember, partyCode, PASSWORD, 6, Instant.EPOCH.plusSeconds(3)))
                .isEqualTo(PartyStore.JoinResult.JOINED);
        assertThat(partyStore.kick(leader, secondMember, "654321")).isEqualTo(PartyStore.KickResult.KICKED);
        assertThat(partyStore.findByAccountId(secondMember)).isEmpty();
        assertThat(partyStore.join(secondMember, partyCode, PASSWORD, 6, Instant.EPOCH.plusSeconds(3)))
                .isEqualTo(PartyStore.JoinResult.WRONG_PASSWORD);
        assertThat(partyStore.findByCode(partyCode).orElseThrow().password()).isEqualTo("654321");
        assertThat(partyStore.join(secondMember, partyCode, "654321", 6, Instant.EPOCH.plusSeconds(4)))
                .isEqualTo(PartyStore.JoinResult.JOINED);
        assertThat(partyStore.leave(leader)).isEqualTo(PartyStore.LeaveResult.DISBANDED);
        assertThat(partyStore.findByAccountId(leader)).isEmpty();
        assertThat(partyStore.findByAccountId(firstMember)).isEmpty();
        assertThat(partyStore.findByAccountId(secondMember)).isEmpty();
        assertThat(partyStore.findByCode(partyCode)).isEmpty();
        assertThat(partyStore.join(UUID.randomUUID(), partyCode, "654321", 6, Instant.now()))
                .isEqualTo(PartyStore.JoinResult.PARTY_NOT_FOUND);
        assertThat(partyStore.create(UUID.randomUUID(), "NEWGROUP", PASSWORD, secondMember, Instant.now()))
                .isEqualTo(PartyStore.CreateResult.CREATED);
    }

    @Test
    void partyQueueIsEnqueuedAndCancelledAsOneAtomicUnit() {
        UUID partyId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID firstMember = UUID.randomUUID();
        UUID secondMember = UUID.randomUUID();
        List<QueueEntry> entries = List.of(
                new QueueEntry(leader, MatchMode.NORMAL, Instant.EPOCH.plusSeconds(21), partyId),
                new QueueEntry(firstMember, MatchMode.NORMAL, Instant.EPOCH.plusSeconds(21), partyId),
                new QueueEntry(secondMember, MatchMode.NORMAL, Instant.EPOCH.plusSeconds(21), partyId)
        );
        assertThat(partyStore.create(partyId, "PQ12RS34", PASSWORD, leader, Instant.EPOCH))
                .isEqualTo(PartyStore.CreateResult.CREATED);
        assertThat(partyStore.join(firstMember, "PQ12RS34", PASSWORD, 6, Instant.EPOCH.plusSeconds(1)))
                .isEqualTo(PartyStore.JoinResult.JOINED);
        assertThat(partyStore.join(secondMember, "PQ12RS34", PASSWORD, 6, Instant.EPOCH.plusSeconds(2)))
                .isEqualTo(PartyStore.JoinResult.JOINED);

        assertThat(store.enqueueParty(entries)).isTrue();
        assertThat(store.oldest(MatchMode.NORMAL, 3)).containsExactlyInAnyOrderElementsOf(entries);
        entries.forEach(entry -> assertThat(store.find(entry.accountId())).contains(entry));
        assertThat(partyStore.kick(leader, secondMember, "654321")).isEqualTo(PartyStore.KickResult.PARTY_QUEUED);
        assertThat(partyStore.leave(firstMember)).isEqualTo(PartyStore.LeaveResult.PARTY_QUEUED);
        assertThat(partyStore.leave(leader)).isEqualTo(PartyStore.LeaveResult.PARTY_QUEUED);
        assertThat(partyStore.findByCode("PQ12RS34").orElseThrow().memberIds())
                .containsExactlyInAnyOrder(leader, firstMember, secondMember);

        assertThat(store.cancelParty(entries)).isEqualTo(QueueStore.CancelResult.CANCELLED);
        entries.forEach(entry -> assertThat(store.find(entry.accountId())).isEmpty());
        assertThat(partyStore.leave(secondMember)).isEqualTo(PartyStore.LeaveResult.LEFT);
        assertThat(partyStore.leave(firstMember)).isEqualTo(PartyStore.LeaveResult.LEFT);
        assertThat(partyStore.leave(leader)).isEqualTo(PartyStore.LeaveResult.DISBANDED);
    }

    @Test
    void reservedPartyCannotBePartiallyCancelled() {
        UUID partyId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        List<QueueEntry> entries = List.of(
                new QueueEntry(leader, MatchMode.NORMAL, Instant.EPOCH.plusSeconds(31), partyId),
                new QueueEntry(member, MatchMode.NORMAL, Instant.EPOCH.plusSeconds(31), partyId)
        );
        assertThat(partyStore.create(partyId, "TU56VW78", PASSWORD, leader, Instant.EPOCH))
                .isEqualTo(PartyStore.CreateResult.CREATED);
        assertThat(partyStore.join(member, "TU56VW78", PASSWORD, 6, Instant.EPOCH.plusSeconds(1)))
                .isEqualTo(PartyStore.JoinResult.JOINED);
        assertThat(store.enqueueParty(entries)).isTrue();
        assertThat(store.reserve(MatchMode.NORMAL, entries, UUID.randomUUID())).isTrue();

        assertThat(store.cancelParty(entries)).isEqualTo(QueueStore.CancelResult.RESERVED);
        entries.forEach(entry -> assertThat(store.reservation(entry.accountId())).isPresent());

        store.requeue(entries);
        assertThat(store.cancelParty(entries)).isEqualTo(QueueStore.CancelResult.CANCELLED);
        assertThat(partyStore.leave(member)).isEqualTo(PartyStore.LeaveResult.LEFT);
        assertThat(partyStore.leave(leader)).isEqualTo(PartyStore.LeaveResult.DISBANDED);
    }

    private boolean reserveTogether(
            List<QueueEntry> entries,
            UUID proposalId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return store.reserve(MatchMode.NORMAL, entries, proposalId);
    }
}
