package com.msspoker.chatservice;

import static org.assertj.core.api.Assertions.*;

import com.msspoker.chatservice.exception.ServiceException;
import com.msspoker.chatservice.service.impl.client.SocialClientImpl;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class SocialClientImplTest {
  HttpServer server;
  AtomicReference<String> body = new AtomicReference<>("{\"isFriend\":true,\"isBlocked\":false}");
  AtomicReference<String> key = new AtomicReference<>();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/internal/friends",
        e -> {
          key.set(e.getRequestHeaders().getFirst("X-Social-Internal-Key"));
          byte[] data = body.get().getBytes();
          e.getResponseHeaders().add("Content-Type", "application/json");
          e.sendResponseHeaders(200, data.length);
          e.getResponseBody().write(data);
          e.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  SocialClientImpl client(String secret) {
    return new SocialClientImpl(
        "http://127.0.0.1:" + server.getAddress().getPort(), secret, Duration.ofMillis(500));
  }

  @Test
  void consumesExactBooleanContractAndSendsInternalCredential() {
    var relation = client("test-internal").relation(UUID.randomUUID(), UUID.randomUUID());
    assertThat(relation.isFriend()).isTrue();
    assertThat(relation.isBlocked()).isFalse();
    assertThat(key.get()).isEqualTo("test-internal");
  }

  @Test
  void missingFieldsDoNotDefaultToFriendOrUnblocked() {
    body.set("{\"isFriend\":true}");
    assertThatThrownBy(() -> client("key").relation(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(ServiceException.class);
    body.set("{\"isFriend\":\"true\",\"isBlocked\":false}");
    assertThatThrownBy(() -> client("key").relation(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void missingCredentialAndNetworkErrorsFailClosed() {
    assertThatThrownBy(() -> client("").relation(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(ServiceException.class);
    server.stop(0);
    assertThatThrownBy(() -> client("key").relation(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(ServiceException.class);
  }
}
