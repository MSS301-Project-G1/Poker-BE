package com.msspoker.chatservice;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.enums.ChatError;
import com.msspoker.chatservice.exception.ChatApiException;
import com.msspoker.chatservice.repository.*;
import com.msspoker.chatservice.service.*;
import com.msspoker.chatservice.service.client.SocialClient;
import com.msspoker.chatservice.service.impl.event.MatchEventConsumer;

import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatServiceApplicationTests extends TestSupport {
    @Autowired ConversationService conversations;
    @Autowired MessageService messages;
    @Autowired MatchLifecycleService lifecycle;
    @Autowired MatchEventConsumer consumer;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @MockitoBean SocialClient social;
    @LocalServerPort int port;

    @BeforeEach
    void friendship() {
        when(social.relation(any(), any())).thenReturn(new SocialClient.Relation(true, false));
    }

    private SendMessageRequest text(String value) {
        return new SendMessageRequest("TEXT", value, null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.msspoker.chatservice.enums.ConversationType.class)
    void profanityIsRejectedForEveryConversationOverRestAndStomp(
            com.msspoker.chatservice.enums.ConversationType type) throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        com.msspoker.chatservice.dto.response.ConversationResponse room;
        if (type == com.msspoker.chatservice.enums.ConversationType.DIRECT) {
            room = conversations.direct(a, b);
        } else {
            UUID match = UUID.randomUUID();
            lifecycle.started(match, List.of(a, b));
            room = conversations.list(a, 0, 20, "MATCH").items().getFirst();
        }
        String body = "{\"messageType\":\"TEXT\",\"content\":\"Đ# mm @\",\"clientMessageId\":\""
                + UUID.randomUUID() + "\"}";
        var response = http(port, "POST", "/api/chat/conversations/" + room.id() + "/messages",
                "gateway-test-key", a, body);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("CHAT_CONTENT_NOT_ALLOWED");

        var client = socketClient();
        try {
            var sender = connect(client, port, a);
            var recipient = connect(client, port, b);
            var senderMessages = new LinkedBlockingQueue<String>();
            var recipientMessages = new LinkedBlockingQueue<String>();
            var errors = new LinkedBlockingQueue<String>();
            subscribe(sender, "/user/queue/chat", senderMessages);
            subscribe(recipient, "/user/queue/chat", recipientMessages);
            subscribe(sender, "/user/queue/chat-errors", errors);
            var headers = new org.springframework.messaging.simp.stomp.StompHeaders();
            headers.setDestination("/app/conversations/" + room.id() + "/send");
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            sender.send(headers, body);
            assertThat(errors.poll(3, TimeUnit.SECONDS)).contains("CHAT_CONTENT_NOT_ALLOWED");
            assertThat(senderMessages.poll(200, TimeUnit.MILLISECONDS)).isNull();
            assertThat(recipientMessages.poll(200, TimeUnit.MILLISECONDS)).isNull();
            assertThat(messages.history(a, room.id(), null, 20).items()).isEmpty();
            assertThat(sender.isConnected()).isTrue();

            // Rejection does not poison the socket or prevent a corrected message.
            sender.send(headers, "{\"messageType\":\"TEXT\",\"content\":\"Xin chào\"}");
            assertThat(senderMessages.poll(3, TimeUnit.SECONDS)).contains("Xin chào");
            assertThat(recipientMessages.poll(3, TimeUnit.SECONDS)).contains("Xin chào");
            assertThat(messages.history(a, room.id(), null, 20).items()).hasSize(1);
            sender.disconnect();
            recipient.disconnect();
        } finally {
            client.stop();
        }
    }

    @Test
    void concurrentDirectCreationReturnsSameConversation() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var x = pool.submit(() -> conversations.direct(a, b));
            var y = pool.submit(() -> conversations.direct(b, a));
            assertThat(x.get().id()).isEqualTo(y.get().id());
            assertThat(conversations.get(a, x.get().id()).memberIds())
                    .containsExactlyInAnyOrder(a, b);
        }
    }

    @Test
    void directRejectsSelfNonFriendAndDependencyError() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertThatThrownBy(() -> conversations.direct(a, a)).isInstanceOf(ChatApiException.class);
        when(social.relation(a, b)).thenReturn(new SocialClient.Relation(false, false));
        assertThatThrownBy(() -> conversations.direct(a, b)).isInstanceOf(ChatApiException.class);
        when(social.relation(a, b))
                .thenThrow(
                        new ChatApiException(
                                ChatError.CHAT_DEPENDENCY_UNAVAILABLE,
                                org.springframework.http.HttpStatus.BAD_GATEWAY,
                                "unavailable"));
        assertThatThrownBy(() -> conversations.direct(a, b)).isInstanceOf(ChatApiException.class);
    }

    @Test
    void outsiderCannotReadListHistoryOrSend() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var room = conversations.direct(a, b);
        assertThat(conversations.list(c, 0, 20).items()).isEmpty();
        assertThatThrownBy(() -> conversations.get(c, room.id()))
                .isInstanceOf(ChatApiException.class);
        assertThatThrownBy(() -> messages.history(c, room.id(), null, 20))
                .isInstanceOf(ChatApiException.class);
        assertThatThrownBy(() -> messages.send(c, room.id(), text("secret")))
                .isInstanceOf(ChatApiException.class);
    }

    @Test
    void typedListsKeepMatchHistoryOutOfDirectChatAndPaginateAfterFiltering() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), outsider = UUID.randomUUID();
        var direct = conversations.direct(a, b);
        for (int i = 0; i < 3; i++) {
            UUID match = UUID.randomUUID();
            lifecycle.started(match, List.of(a, b));
            lifecycle.finished(match, Instant.now());
        }
        var filtered = conversations.list(a, 0, 1, "DIRECT");
        assertThat(filtered.items()).extracting(r -> r.id()).containsExactly(direct.id());
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.totalPages()).isEqualTo(1);
        assertThat(conversations.list(a, 0, 20, "MATCH").items()).hasSize(3);
        assertThat(conversations.list(a, 0, 20).items()).hasSize(4);
        assertThat(conversations.list(outsider, 0, 20, "DIRECT").items()).isEmpty();
        assertThatThrownBy(() -> conversations.list(a, 0, 20, "INVALID"))
                .isInstanceOf(ChatApiException.class);
    }

    @Test
    void permissionRecheckedOnEveryDirectSendButHistoryRemainsReadable() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var room = conversations.direct(a, b);
        messages.send(a, room.id(), text("before block"));
        when(social.relation(any(), any())).thenReturn(new SocialClient.Relation(false, true));
        assertThatThrownBy(() -> messages.send(a, room.id(), text("after block")))
                .isInstanceOf(ChatApiException.class);
        assertThat(messages.history(b, room.id(), null, 20).items()).hasSize(1);
    }

    @Test
    void textIsTrimmedAndBlankLongOrNonTextRejected() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var room = conversations.direct(a, b);
        assertThat(messages.send(a, room.id(), text("  hello  ")).content()).isEqualTo("hello");
        assertThatThrownBy(() -> messages.send(a, room.id(), text(" ")))
                .isInstanceOf(ChatApiException.class);
        assertThatThrownBy(() -> messages.send(a, room.id(), text("a".repeat(2001))))
                .isInstanceOf(ChatApiException.class);
        assertThatThrownBy(
                        () ->
                                messages.send(
                                        a, room.id(), new SendMessageRequest("STICKER", "x", null)))
                .isInstanceOf(ChatApiException.class);
    }

    @Test
    void concurrentClientRetryStoresOneMessageAndConflictingReuseFails() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var room = conversations.direct(a, b);
        UUID client = UUID.randomUUID();
        var request = new SendMessageRequest("TEXT", "same", client);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var x = pool.submit(() -> messages.send(a, room.id(), request));
            var y = pool.submit(() -> messages.send(a, room.id(), request));
            assertThat(x.get().id()).isEqualTo(y.get().id());
        }
        assertThat(messages.history(a, room.id(), null, 20).items()).hasSize(1);
        assertThatThrownBy(
                        () ->
                                messages.send(
                                        a,
                                        room.id(),
                                        new SendMessageRequest("TEXT", "different", client)))
                .isInstanceOf(ChatApiException.class);
    }

    @Test
    void historyCursorIsStableWhenTimestampsTie() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var room = conversations.direct(a, b);
        for (int i = 0; i < 5; i++) messages.send(a, room.id(), text("m" + i));
        jdbc.update(
                "UPDATE messages SET created_at=? WHERE conversation_id=?",
                java.sql.Timestamp.from(Instant.parse("2026-10-17T00:00:00Z")),
                room.id());
        var first = messages.history(a, room.id(), null, 2);
        var second = messages.history(a, room.id(), first.nextCursor(), 2);
        var third = messages.history(a, room.id(), second.nextCursor(), 2);
        var ids = new HashSet<UUID>();
        first.items().forEach(m -> ids.add(m.id()));
        second.items().forEach(m -> ids.add(m.id()));
        third.items().forEach(m -> ids.add(m.id()));
        assertThat(ids).hasSize(5);
        assertThat(third.nextCursor()).isNull();
        assertThatThrownBy(() -> messages.history(a, room.id(), "bad!", 20))
                .isInstanceOf(ChatApiException.class);
    }

    @Test
    void matchClosesAndDoesNotConsultSocial() {
        UUID match = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        lifecycle.started(match, List.of(a, b));
        var room = conversations.list(a, 0, 20).items().getFirst();
        clearInvocations(social);
        messages.send(a, room.id(), text("game"));
        verifyNoInteractions(social);
        lifecycle.finished(match, Instant.now());
        assertThatThrownBy(() -> messages.send(b, room.id(), text("late")))
                .isInstanceOf(ChatApiException.class);
        assertThat(messages.history(b, room.id(), null, 20).items()).hasSize(1);
    }

    @Test
    void finishedBeforeStartedNeverOpensWritableRoom() {
        UUID match = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        lifecycle.finished(match, Instant.now());
        lifecycle.started(match, List.of(a, b));
        var room = conversations.list(a, 0, 20).items().getFirst();
        assertThat(room.closedAt()).isNotNull();
        assertThatThrownBy(() -> messages.send(a, room.id(), text("late")))
                .isInstanceOf(ChatApiException.class);
    }

    private Message event(UUID event, UUID match, UUID a, UUID b) {
        var p =
                Map.of(
                        "matchId",
                        match,
                        "tableId",
                        UUID.randomUUID(),
                        "sourceId",
                        UUID.randomUUID(),
                        "mode",
                        "NORMAL",
                        "playerIds",
                        List.of(a, b));
        return new Message(
                json.writeValueAsBytes(
                        new com.msspoker.common.event.EventEnvelope<>(
                                event, "match.started", Instant.now(), p)),
                new MessageProperties());
    }

    @Test
    void duplicateEventHasOneConversationAndConflictingMembershipRollsBackReceipt() {
        UUID match = UUID.randomUUID(),
                a = UUID.randomUUID(),
                b = UUID.randomUUID(),
                id = UUID.randomUUID();
        Message e = event(id, match, a, b);
        consumer.started(e);
        consumer.started(e);
        assertThat(conversations.list(a, 0, 20).items()).hasSize(1);
        UUID bad = UUID.randomUUID();
        assertThatThrownBy(() -> consumer.started(event(bad, match, a, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM processed_events WHERE event_id=?",
                                Long.class,
                                bad))
                .isZero();
    }

    @Test
    void restRejectsForgedIdentityAndValidatesBody() throws Exception {
        UUID a = UUID.randomUUID();
        assertThat(http(port, "GET", "/api/chat/conversations", null, a, null).statusCode())
                .isEqualTo(403);
        assertThat(
                        http(
                                        port,
                                        "POST",
                                        "/api/chat/conversations/direct",
                                        "gateway-test-key",
                                        a,
                                        "{}")
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        http(port, "GET", "/api/chat/conversations", "gateway-test-key", a, null)
                                .statusCode())
                .isEqualTo(200);
    }

    @Test
    void socketDeliversOnlyToMembersAndReportsBusinessErrorWithoutClosing() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var room = conversations.direct(a, b);
        var client = socketClient();
        try {
            var one = connect(client, port, a);
            var two = connect(client, port, b);
            var outsider = connect(client, port, c);
            var qa = new LinkedBlockingQueue<String>();
            var qb = new LinkedBlockingQueue<String>();
            var qc = new LinkedBlockingQueue<String>();
            var errors = new LinkedBlockingQueue<String>();
            subscribe(one, "/user/queue/chat", qa);
            subscribe(two, "/user/queue/chat", qb);
            subscribe(outsider, "/user/queue/chat", qc);
            subscribe(outsider, "/user/queue/chat-errors", errors);
            var headers = new org.springframework.messaging.simp.stomp.StompHeaders();
            headers.setDestination("/app/conversations/" + room.id() + "/send");
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            one.send(headers, "{\"messageType\":\"TEXT\",\"content\":\"socket hello\"}");
            assertThat(qa.poll(3, TimeUnit.SECONDS)).contains("socket hello");
            assertThat(qb.poll(3, TimeUnit.SECONDS)).contains("socket hello");
            assertThat(qc.poll(200, TimeUnit.MILLISECONDS)).isNull();
            outsider.send(headers, "{\"messageType\":\"TEXT\",\"content\":\"intrusion\"}");
            assertThat(errors.poll(3, TimeUnit.SECONDS)).contains("CHAT_FORBIDDEN");
            assertThat(outsider.isConnected()).isTrue();
            one.disconnect();
            two.disconnect();
            outsider.disconnect();
        } finally {
            client.stop();
        }
    }

    @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;

    @Test
    void malformedRabbitEventGoesToDlqAfterFiniteRetries() {
        byte[] malformed = "{not valid JSON}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        rabbit.send(
                "poker.events", "match.started", new Message(malformed, new MessageProperties()));
        Message dead = rabbit.receive("chat-service.match.started.dlq", 30000);
        assertThat(dead).isNotNull();
        assertThat(dead.getBody()).isEqualTo(malformed);
    }

    @Test
    void expiredJwtAndCrossUserSubscriptionAreRejected() throws Exception {
        var client = socketClient();
        try {
            var headers = new org.springframework.messaging.simp.stomp.StompHeaders();
            headers.add(
                    "Authorization",
                    "Bearer " + token(UUID.randomUUID(), Instant.now().minusSeconds(60)));
            assertThatThrownBy(
                            () ->
                                    client.connectAsync(
                                                    "ws://localhost:" + port + "/ws/chat",
                                                    new org.springframework.web.socket
                                                            .WebSocketHttpHeaders(),
                                                    headers,
                                                    new org.springframework.messaging.simp.stomp
                                                            .StompSessionHandlerAdapter() {})
                                            .get(3, TimeUnit.SECONDS))
                    .isInstanceOf(Exception.class);
            var session = connect(client, port, UUID.randomUUID());
            session.subscribe(
                    "/queue/private-user-target",
                    new org.springframework.messaging.simp.stomp.StompFrameHandler() {
                        public java.lang.reflect.Type getPayloadType(
                                org.springframework.messaging.simp.stomp.StompHeaders h) {
                            return String.class;
                        }

                        public void handleFrame(
                                org.springframework.messaging.simp.stomp.StompHeaders h,
                                Object p) {}
                    });
            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(3))
                    .until(() -> !session.isConnected());
        } finally {
            client.stop();
        }
    }

    @Test
    void realRabbitStartAndFinishUpdateSameMatchConversation() {
        UUID match = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        rabbit.send("poker.events", "match.started", event(UUID.randomUUID(), match, a, b));
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> conversations.list(a, 0, 20).items().size() == 1);
        var room = conversations.list(a, 0, 20).items().getFirst();
        var payload =
                Map.of(
                        "matchId",
                        match,
                        "tableId",
                        UUID.randomUUID(),
                        "sourceId",
                        UUID.randomUUID(),
                        "mode",
                        "NORMAL",
                        "finishedAt",
                        Instant.now());
        var envelope =
                new com.msspoker.common.event.EventEnvelope<>(
                        UUID.randomUUID(), "match.finished", Instant.now(), payload);
        rabbit.send(
                "poker.events",
                "match.finished",
                new Message(json.writeValueAsBytes(envelope), new MessageProperties()));
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> conversations.get(a, room.id()).closedAt() != null);
    }
}
