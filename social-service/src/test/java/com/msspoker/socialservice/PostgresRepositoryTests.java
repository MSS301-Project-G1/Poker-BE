package com.msspoker.socialservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.msspoker.socialservice.repository.EventReceiptRepository;
import com.msspoker.socialservice.service.SocialService;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs against an explicitly supplied, disposable PostgreSQL database, without RabbitMQ. */
@EnabledIfSystemProperty(named = "structure.test.postgres-url", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "eureka.client.enabled=false",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.rabbitmq.dynamic=false",
    "social.events.publisher-enabled=false",
    "social.gateway-key=repository-test-key",
    "social.internal-key=repository-internal-key"
})
class PostgresRepositoryTests {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getProperty("structure.test.postgres-url") + "social_structure");
    registry.add("spring.datasource.username", () -> "structure_test");
    registry.add("spring.datasource.password", () -> "");
  }

  @Autowired SocialService social;
  @Autowired EventReceiptRepository receipts;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void oppositeRequestsSerializeAndBlockStillRemovesBothFriendships() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> social.send(a, b));
      var second = pool.submit(() -> social.send(b, a));
      assertThat(first.get(10, TimeUnit.SECONDS).id()).isEqualTo(second.get(10, TimeUnit.SECONDS).id());
    }
    assertThat(social.relation(a, b).isFriend()).isTrue();
    social.block(a, b);
    assertThat(social.internalFriends(a)).isEmpty();
    assertThat(social.internalFriends(b)).isEmpty();
    assertThat(social.relation(a, b).isBlocked()).isTrue();
  }

  @Test
  void receiptClaimIsAtomicAndRollbackAllowsRedelivery() throws Exception {
    var tx = new TransactionTemplate(transactions);
    UUID id = UUID.randomUUID();
    tx.executeWithoutResult(status -> {
      assertThat(receipts.claim(id, "notification.requested")).isTrue();
      status.setRollbackOnly();
    });
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> tx.execute(status -> receipts.claim(id, "notification.requested")));
      var second = pool.submit(() -> tx.execute(status -> receipts.claim(id, "notification.requested")));
      assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
    }
  }
}
