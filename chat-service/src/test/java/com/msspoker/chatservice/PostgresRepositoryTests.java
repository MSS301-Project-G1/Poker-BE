package com.msspoker.chatservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.repository.EventReceiptRepository;
import com.msspoker.chatservice.repository.MatchLifecycleRepository;
import com.msspoker.chatservice.service.ChatService;
import com.msspoker.chatservice.service.MatchLifecycleService;
import com.msspoker.chatservice.service.client.SocialClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs against an explicitly supplied, disposable PostgreSQL database, without RabbitMQ. */
@EnabledIfSystemProperty(named = "structure.test.postgres-url", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "eureka.client.enabled=false",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.rabbitmq.dynamic=false",
    "chat.gateway-key=repository-test-key",
    "chat.internal-key=repository-internal-key"
})
class PostgresRepositoryTests {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getProperty("structure.test.postgres-url") + "chat_structure");
    registry.add("spring.datasource.username", () -> "structure_test");
    registry.add("spring.datasource.password", () -> "");
  }

  @Autowired ChatService chat;
  @Autowired MatchLifecycleService lifecycle;
  @Autowired MatchLifecycleRepository lifecycles;
  @Autowired EventReceiptRepository receipts;
  @Autowired PlatformTransactionManager transactions;
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
      var first = pool.submit(() -> chat.direct(a, b));
      var second = pool.submit(() -> chat.direct(b, a));
      room = first.get(10, TimeUnit.SECONDS).id();
      assertThat(second.get(10, TimeUnit.SECONDS).id()).isEqualTo(room);
      var request = new SendMessageRequest("TEXT", "same message", UUID.randomUUID());
      var send = pool.submit(() -> chat.send(a, room, request));
      var retry = pool.submit(() -> chat.send(a, room, request));
      assertThat(send.get(10, TimeUnit.SECONDS).id()).isEqualTo(retry.get(10, TimeUnit.SECONDS).id());
    }
    assertThat(chat.history(b, room, null, 20).items()).hasSize(1);
  }

  @Test
  void lifecyclePreservesFirstFinishAndOutOfOrderStartCreatesReadOnlyRoom() {
    UUID match = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    Instant finished = Instant.parse("2026-11-13T00:00:00Z");
    lifecycle.finished(match, finished);
    lifecycle.finished(match, finished.plusSeconds(10));
    lifecycle.started(match, List.of(a, b));
    var room = chat.list(a, 0, 20, "MATCH").items().getFirst();
    assertThat(room.closedAt()).isEqualTo(finished);
    assertThat(lifecycles.findById(match).orElseThrow().getFinishedAt()).isEqualTo(finished);
    assertThatThrownBy(() -> chat.send(a, room.id(), new SendMessageRequest("TEXT", "late", null)))
        .isInstanceOf(com.msspoker.chatservice.exception.ServiceException.class);
  }

  @Test
  void receiptClaimIsAtomicAndRollbackAllowsRedelivery() throws Exception {
    var tx = new TransactionTemplate(transactions);
    UUID id = UUID.randomUUID();
    tx.executeWithoutResult(status -> {
      assertThat(receipts.claim(id, "match.started")).isTrue();
      status.setRollbackOnly();
    });
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> tx.execute(status -> receipts.claim(id, "match.started")));
      var second = pool.submit(() -> tx.execute(status -> receipts.claim(id, "match.started")));
      assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
    }
  }
}
