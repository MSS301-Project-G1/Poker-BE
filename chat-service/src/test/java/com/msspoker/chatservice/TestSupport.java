package com.msspoker.chatservice;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

abstract class TestSupport {
  static final GenericContainer<?> DB =
      new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
          .withEnv("POSTGRES_DB", "test_db")
          .withEnv("POSTGRES_USER", "test_user")
          .withEnv("POSTGRES_PASSWORD", "test_password")
          .withExposedPorts(5432);
  static final GenericContainer<?> RABBIT =
      new GenericContainer<>(DockerImageName.parse("rabbitmq:4.1-management-alpine"))
          .withEnv("RABBITMQ_DEFAULT_USER", "test_user")
          .withEnv("RABBITMQ_DEFAULT_PASS", "test_password")
          .withExposedPorts(5672)
          .waitingFor(
              org.testcontainers.containers.wait.strategy.Wait.forLogMessage(
                  ".*Server startup complete.*", 1));
  static final RSAKey KEY;
  static final HttpServer JWKS;

  static {
    try {
      DB.start();
      RABBIT.start();
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var pair = generator.generateKeyPair();
      KEY =
          new RSAKey.Builder((RSAPublicKey) pair.getPublic())
              .privateKey((RSAPrivateKey) pair.getPrivate())
              .keyID("test")
              .build();
      JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      JWKS.createContext(
          "/jwks",
          e -> {
            byte[] bytes =
                new JWKSet(KEY.toPublicJWK())
                    .toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type", "application/json");
            e.sendResponseHeaders(200, bytes.length);
            e.getResponseBody().write(bytes);
            e.close();
          });
      JWKS.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:postgresql://" + DB.getHost() + ":" + DB.getMappedPort(5432) + "/test_db");
    r.add("spring.datasource.username", () -> "test_user");
    r.add("spring.datasource.password", () -> "test_password");
    r.add("spring.rabbitmq.host", RABBIT::getHost);
    r.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
    r.add("spring.rabbitmq.username", () -> "test_user");
    r.add("spring.rabbitmq.password", () -> "test_password");
    r.add("chat.gateway-key", () -> "gateway-test-key");
    r.add("chat.internal-key", () -> "internal-test-key");
    r.add(
        "chat.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    r.add("chat.jwt.issuer", () -> "http://test-issuer");
    r.add("chat.jwt.audience", () -> "poker");
    r.add("chat.ws.allowed-origins", () -> "http://localhost:5173");
  }

  static String token(UUID id, Instant expiry) throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .subject(id.toString())
            .issuer("http://test-issuer")
            .audience("poker")
            .expirationTime(Date.from(expiry))
            .issueTime(new Date())
            .build();
    var jwt =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
    jwt.sign(new RSASSASigner(KEY));
    return jwt.serialize();
  }

  static HttpResponse<String> http(
      int port, String method, String path, String key, UUID actor, String body) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json");
    if (key != null)
      b.header(path.startsWith("/internal") ? "X-Chat-Internal-Key" : "X-Chat-Gateway-Key", key);
    if (actor != null) b.header("X-User-Id", actor.toString());
    return HttpClient.newHttpClient()
        .send(
            b.method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  static final Map<StompSession, UUID> SOCKET_USERS = new ConcurrentHashMap<>();

  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.messaging.simp.user.SimpUserRegistry users;

  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler broker;

  static WebSocketStompClient socketClient() {
    var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
    scheduler.setDaemon(true);
    scheduler.initialize();
    var client =
        new WebSocketStompClient(new StandardWebSocketClient()) {
          public void stop() {
            super.stop();
            scheduler.shutdown();
          }
        };
    client.setTaskScheduler(scheduler);
    client.setMessageConverter(
        new org.springframework.messaging.converter.AbstractMessageConverter(
            org.springframework.util.MimeTypeUtils.ALL) {
          protected boolean supports(Class<?> type) {
            return type == String.class;
          }

          protected Object convertFromInternal(
              org.springframework.messaging.Message<?> m, Class<?> type, Object hint) {
            return new String((byte[]) m.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
          }

          protected Object convertToInternal(
              Object p, org.springframework.messaging.MessageHeaders h, Object hint) {
            return p.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
          }
        });
    client.setDefaultHeartbeat(new long[] {0, 0});
    return client;
  }

  static StompSession connect(WebSocketStompClient client, int port, UUID id) throws Exception {
    var h = new StompHeaders();
    h.add("Authorization", "Bearer " + token(id, Instant.now().plusSeconds(60)));
    var session =
        client
            .connectAsync(
                "ws://localhost:" + port + "/ws/chat",
                new org.springframework.web.socket.WebSocketHttpHeaders(),
                h,
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    SOCKET_USERS.put(session, id);
    return session;
  }

  void subscribe(StompSession session, String destination, BlockingQueue<String> received) {
    var subscription =
        session.subscribe(
            destination,
            new StompFrameHandler() {
              public java.lang.reflect.Type getPayloadType(StompHeaders h) {
                return String.class;
              }

              public void handleFrame(StompHeaders h, Object p) {
                received.add((String) p);
              }
            });
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      var user = users.getUser(SOCKET_USERS.get(session).toString());
      if (user != null)
        for (var serverSession : user.getSessions()) {
          var header =
              org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
                  org.springframework.messaging.simp.SimpMessageType.MESSAGE);
          header.setDestination(
              destination.substring("/user".length()) + "-user" + serverSession.getId());
          var found =
              broker
                  .getSubscriptionRegistry()
                  .findSubscriptions(
                      org.springframework.messaging.support.MessageBuilder.createMessage(
                          new byte[0], header.getMessageHeaders()));
          if (found
              .getOrDefault(serverSession.getId(), List.of())
              .contains(subscription.getSubscriptionId())) return;
        }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    throw new AssertionError("Broker subscription timeout");
  }
}
