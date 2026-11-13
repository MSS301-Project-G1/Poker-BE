package com.msspoker.chatservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.repository.EventReceiptRepository;
import com.msspoker.chatservice.repository.MatchLifecycleRepository;
import com.msspoker.chatservice.service.ConversationService;
import com.msspoker.chatservice.service.MatchLifecycleService;
import com.msspoker.chatservice.service.MessageService;
import com.msspoker.chatservice.service.client.SocialClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Runs against an explicitly supplied, disposable PostgreSQL database, without RabbitMQ. */
@EnabledIfSystemProperty(named = "structure.test.postgres-url", matches = ".+")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "eureka.client.enabled=false",
            "spring.rabbitmq.listener.simple.auto-startup=false",
            "spring.rabbitmq.dynamic=false",
            "chat.gateway-key=repository-test-key",
            "chat.internal-key=repository-internal-key"
        })
class PostgresRepositoryTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> System.getProperty("structure.test.postgres-url") + "chat_structure");
        registry.add("spring.datasource.username", () -> "structure_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired ConversationService conversations;
    @Autowired MessageService messages;
    @Autowired MatchLifecycleService lifecycle;
    @Autowired MatchLifecycleRepository lifecycles;
    @Autowired EventReceiptRepository receipts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    @LocalServerPort int port;
    @MockitoBean SocialClient social;

    @BeforeEach
    void friendship() {
        when(social.relation(any(), any())).thenReturn(new SocialClient.Relation(true, false));
    }

    @Test
    void concurrentDirectCreationAndMessageRetryRemainIdempotent() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID room;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> conversations.direct(a, b));
            var second = pool.submit(() -> conversations.direct(b, a));
            room = first.get(10, TimeUnit.SECONDS).id();
            assertThat(second.get(10, TimeUnit.SECONDS).id()).isEqualTo(room);
            var request = new SendMessageRequest("TEXT", "same message", UUID.randomUUID());
            var send = pool.submit(() -> messages.send(a, room, request));
            var retry = pool.submit(() -> messages.send(a, room, request));
            assertThat(send.get(10, TimeUnit.SECONDS).id())
                    .isEqualTo(retry.get(10, TimeUnit.SECONDS).id());
        }
        assertThat(messages.history(b, room, null, 20).items()).hasSize(1);
    }

    @Test
    void lifecyclePreservesFirstFinishAndOutOfOrderStartCreatesReadOnlyRoom() {
        UUID match = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        Instant finished = Instant.parse("2026-11-13T00:00:00Z");
        lifecycle.finished(match, finished);
        lifecycle.finished(match, finished.plusSeconds(10));
        lifecycle.started(match, List.of(a, b));
        var room = conversations.list(a, 0, 20, "MATCH").items().getFirst();
        assertThat(room.closedAt()).isEqualTo(finished);
        assertThat(lifecycles.findById(match).orElseThrow().getFinishedAt()).isEqualTo(finished);
        assertThatThrownBy(
                        () ->
                                messages.send(
                                        a, room.id(), new SendMessageRequest("TEXT", "late", null)))
                .isInstanceOf(com.msspoker.chatservice.exception.ChatApiException.class);
    }

    @Test
    void receiptClaimIsAtomicAndRollbackAllowsRedelivery() throws Exception {
        var tx = new TransactionTemplate(transactions);
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(
                status -> {
                    assertThat(receipts.claim(id, "match.started")).isTrue();
                    status.setRollbackOnly();
                });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(() -> tx.execute(status -> receipts.claim(id, "match.started")));
            var second =
                    pool.submit(() -> tx.execute(status -> receipts.claim(id, "match.started")));
            assertThat(first.get(10, TimeUnit.SECONDS))
                    .isNotEqualTo(second.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void cursorAndTypeFilteringKeepTheirExistingContractAfterServiceSplit() {
        UUID accountId = UUID.randomUUID(), friendId = UUID.randomUUID();
        var room = conversations.direct(accountId, friendId);
        lifecycle.started(UUID.randomUUID(), List.of(accountId, friendId));
        assertThat(conversations.list(accountId, 0, 1, "DIRECT").items())
                .extracting(item -> item.id())
                .containsExactly(room.id());
        assertThat(conversations.list(accountId, 0, 1, "DIRECT").totalElements()).isEqualTo(1);
        for (int index = 0; index < 5; index++) {
            messages.send(
                    accountId, room.id(), new SendMessageRequest("TEXT", "message " + index, null));
        }
        jdbc.update(
                "UPDATE messages SET created_at=? WHERE conversation_id=?",
                java.sql.Timestamp.from(Instant.parse("2026-11-13T00:00:00Z")),
                room.id());
        var first = messages.history(friendId, room.id(), null, 2);
        var second = messages.history(friendId, room.id(), first.nextCursor(), 2);
        var third = messages.history(friendId, room.id(), second.nextCursor(), 2);
        var ids = new HashSet<UUID>();
        first.items().forEach(message -> ids.add(message.id()));
        second.items().forEach(message -> ids.add(message.id()));
        third.items().forEach(message -> ids.add(message.id()));
        assertThat(ids).hasSize(5);
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    void restKeepsTextPayloadAndErrorCodesAfterEnumAndPropertiesRefactor() throws Exception {
        UUID accountId = UUID.randomUUID(), friendId = UUID.randomUUID();
        var room = conversations.direct(accountId, friendId);
        URI uri =
                URI.create(
                        "http://127.0.0.1:"
                                + port
                                + "/api/chat/conversations/"
                                + room.id()
                                + "/messages");
        var client = HttpClient.newHttpClient();
        var request =
                HttpRequest.newBuilder(uri)
                        .header("Content-Type", "application/json")
                        .header("X-Chat-Gateway-Key", "repository-test-key")
                        .header("X-User-Id", accountId.toString())
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"messageType\":\"TEXT\",\"content\":\"hello\"}"))
                        .build();
        var sent = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(sent.statusCode()).isEqualTo(201);
        assertThat(json.readTree(sent.body()).path("messageType").asString()).isEqualTo("TEXT");
        var invalid =
                HttpRequest.newBuilder(uri)
                        .header("Content-Type", "application/json")
                        .header("X-Chat-Gateway-Key", "repository-test-key")
                        .header("X-User-Id", accountId.toString())
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"messageType\":\"TEXT\",\"content\":\" \"}"))
                        .build();
        var rejected = client.send(invalid, HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(json.readTree(rejected.body()).path("code").asString())
                .isEqualTo("CHAT_INVALID_REQUEST");
    }
}
