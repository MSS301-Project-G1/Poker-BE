package com.msspoker.socialservice;

import static org.assertj.core.api.Assertions.*;

import com.msspoker.common.event.EventEnvelope;
import com.msspoker.socialservice.dto.event.Notification;
import com.msspoker.socialservice.exception.ServiceException;
import com.msspoker.socialservice.service.impl.event.NotificationConsumer;
import com.msspoker.socialservice.service.SocialService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialServiceApplicationTests extends TestSupport {
  @Autowired SocialService social;
  @Autowired JdbcTemplate jdbc;
  @Autowired NotificationConsumer consumer;
  @Autowired JsonMapper json;
  @LocalServerPort int port;

  @Test
  void acceptCreatesBothDirectionsAndRetryDoesNotDuplicateOutbox() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var r = social.send(a, b);
    social.respond(b, r.id(), "ACCEPTED");
    social.respond(b, r.id(), "ACCEPTED");
    assertThat(social.relation(a, b).isFriend()).isTrue();
    assertThat(social.internalFriends(a)).containsExactly(b);
    assertThat(social.internalFriends(b)).containsExactly(a);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM social_outbox WHERE body LIKE ?",
                Long.class,
                "%" + r.id() + "%"))
        .isEqualTo(2);
  }

  @Test
  void reverseRequestAcceptsExistingPending() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var r = social.send(a, b);
    var reverse = social.send(b, a);
    assertThat(reverse.id()).isEqualTo(r.id());
    assertThat(reverse.status()).isEqualTo("ACCEPTED");
    assertThat(social.relation(a, b).isFriend()).isTrue();
  }

  @Test
  void onlyRecipientCanAcceptAndOnlySenderCanCancel() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    var r = social.send(a, b);
    assertThatThrownBy(() -> social.respond(a, r.id(), "ACCEPTED"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> social.respond(c, r.id(), "DECLINED"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> social.respond(b, r.id(), "CANCELLED"))
        .isInstanceOf(ServiceException.class);
    social.respond(a, r.id(), "CANCELLED");
    assertThat(social.relation(a, b).isFriend()).isFalse();
  }

  @Test
  void declineIsTerminalAndNewRequestIsAllowed() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var r = social.send(a, b);
    social.respond(b, r.id(), "DECLINED");
    assertThatThrownBy(() -> social.respond(b, r.id(), "ACCEPTED"))
        .isInstanceOf(ServiceException.class);
    assertThat(social.send(a, b).id()).isNotEqualTo(r.id());
  }

  @Test
  void blockRemovesBothDirectionsAndUnblockDoesNotRestore() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var r = social.send(a, b);
    social.respond(b, r.id(), "ACCEPTED");
    social.block(a, b);
    assertThat(social.relation(b, a).isBlocked()).isTrue();
    assertThat(social.relation(a, b).isFriend()).isFalse();
    assertThat(social.internalFriends(a)).isEmpty();
    assertThat(social.internalFriends(b)).isEmpty();
    assertThatThrownBy(() -> social.send(b, a)).isInstanceOf(ServiceException.class);
    social.unblock(b, a);
    assertThat(social.relation(a, b).isBlocked()).isTrue();
    social.unblock(a, b);
    assertThat(social.relation(a, b).isFriend()).isFalse();
  }

  @Test
  void blockCancelsPendingAndSelfOperationsAreRejected() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var r = social.send(a, b);
    social.block(b, a);
    assertThat(social.requests(a, "OUTGOING", 0, 20).items().getFirst().status())
        .isEqualTo("CANCELLED");
    assertThatThrownBy(() -> social.respond(b, r.id(), "ACCEPTED"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> social.send(a, a)).isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> social.block(a, a)).isInstanceOf(ServiceException.class);
  }

  @Test
  void concurrentOppositeRequestsHaveOneFriendship() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> social.send(a, b));
      var second = pool.submit(() -> social.send(b, a));
      assertThat(first.get().id()).isEqualTo(second.get().id());
    }
    assertThat(social.internalFriends(a)).containsExactly(b);
  }

  @Test
  void blockRacingAcceptLeavesNoFriendship() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var r = social.send(a, b);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                try {
                  social.respond(b, r.id(), "ACCEPTED");
                } catch (ServiceException ignored) {
                }
              });
      var second = pool.submit(() -> social.block(a, b));
      first.get();
      second.get();
    }
    assertThat(social.relation(a, b).isBlocked()).isTrue();
    assertThat(social.internalFriends(a)).isEmpty();
    assertThat(social.internalFriends(b)).isEmpty();
  }

  @Test
  void listIsPaginatedAndScopedToActor() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    social.send(a, b);
    assertThat(social.requests(a, "OUTGOING", 0, 1).items()).hasSize(1);
    assertThat(social.requests(UUID.randomUUID(), "OUTGOING", 0, 20).items()).isEmpty();
    assertThatThrownBy(() -> social.friends(a, 0, 101)).isInstanceOf(ServiceException.class);
  }

  @Test
  void ingressRejectsForgedUserAndInternalRequiresSeparateKey() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    assertThat(http(port, "GET", "/api/friends", null, a, null).statusCode()).isEqualTo(403);
    assertThat(http(port, "GET", "/api/friends", "gateway-test-key", a, null).statusCode())
        .isEqualTo(200);
    String path = "/internal/friends/" + a + "/with/" + b;
    assertThat(http(port, "GET", path, "gateway-test-key", null, null).statusCode()).isEqualTo(403);
    var response = http(port, "GET", path, "internal-test-key", null, null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json.readTree(response.body()).has("isBlocked")).isTrue();
  }

  private Message notification(UUID event, UUID account) {
    var envelope =
        new EventEnvelope<>(
            event,
            "notification.requested",
            Instant.now(),
            new Notification(
                List.of(account), "MATCH_FOUND", "Match found", "", json.createObjectNode()));
    return new Message(json.writeValueAsBytes(envelope), new MessageProperties());
  }

  @Test
  void notificationGoesOnlyToRecipientAndDuplicateIsIgnored() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), id = UUID.randomUUID();
    var client = socketClient();
    try {
      var one = connect(client, port, a);
      var two = connect(client, port, b);
      var qa = new LinkedBlockingQueue<String>();
      var qb = new LinkedBlockingQueue<String>();
      subscribe(one, "/user/queue/notifications", qa);
      subscribe(two, "/user/queue/notifications", qb);
      consumer.receive(notification(id, a));
      assertThat(qa.poll(3, TimeUnit.SECONDS)).contains(id.toString());
      consumer.receive(notification(id, a));
      assertThat(qa.poll(200, TimeUnit.MILLISECONDS)).isNull();
      assertThat(qb.poll(200, TimeUnit.MILLISECONDS)).isNull();
      one.disconnect();
      two.disconnect();
    } finally {
      client.stop();
    }
  }

  @Test
  void invalidNotificationDoesNotClaimReceipt() {
    UUID id = UUID.randomUUID();
    var body =
        "{\"eventId\":\""
            + id
            + "\",\"eventType\":\"notification.requested\",\"occurredAt\":\"2026-10-17T00:00:00Z\",\"payload\":{}}";
    assertThatThrownBy(
            () -> consumer.receive(new Message(body.getBytes(), new MessageProperties())))
        .isInstanceOf(RuntimeException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id=?", Long.class, id))
        .isZero();
  }

  @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;

  @Test
  void malformedRabbitEventGoesToDlqAfterFiniteRetries() {
    byte[] malformed = "{not valid JSON}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    rabbit.send(
        "poker.events", "notification.requested", new Message(malformed, new MessageProperties()));
    Message dead = rabbit.receive("social-service.notification.requested.dlq", 30000);
    assertThat(dead).isNotNull();
    assertThat(dead.getBody()).isEqualTo(malformed);
  }

  @Test
  void expiredJwtAndCrossUserSubscriptionAreRejected() throws Exception {
    var client = socketClient();
    try {
      var headers = new org.springframework.messaging.simp.stomp.StompHeaders();
      headers.add(
          "Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().minusSeconds(60)));
      assertThatThrownBy(
              () ->
                  client
                      .connectAsync(
                          "ws://localhost:" + port + "/ws/social",
                          new org.springframework.web.socket.WebSocketHttpHeaders(),
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
                org.springframework.messaging.simp.stomp.StompHeaders h, Object p) {}
          });
      org.awaitility.Awaitility.await()
          .atMost(java.time.Duration.ofSeconds(3))
          .until(() -> !session.isConnected());
    } finally {
      client.stop();
    }
  }

  @Test
  void friendOutboxPublishesConfirmedNotificationThroughRabbit() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var client = socketClient();
    try {
      var target = connect(client, port, b);
      var received = new LinkedBlockingQueue<String>();
      subscribe(target, "/user/queue/notifications", received);
      var request = social.send(a, b);
      assertThat(received.poll(8, TimeUnit.SECONDS))
          .contains("FRIEND_REQUEST")
          .contains(request.id().toString());
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(3))
          .until(
              () ->
                  jdbc.queryForObject(
                          "SELECT count(*) FROM social_outbox WHERE body LIKE ? AND published_at IS"
                              + " NOT NULL",
                          Long.class,
                          "%" + request.id() + "%")
                      == 1);
      target.disconnect();
    } finally {
      client.stop();
    }
  }
}
