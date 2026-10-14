package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.RegistrationEvent;
import com.msspoker.authservice.entity.RegistrationOtp;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.RegistrationEventRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.service.RegistrationEventService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.MessagingConfig;
import com.msspoker.authservice.shared.infrastructure.mail.RegistrationMailSender;
import com.msspoker.common.event.AccountRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfiguration.class, RegistrationInfrastructureConfiguration.class})
class RegistrationInfrastructureTests {
    @Autowired private AccountRepository accounts;
    @Autowired private RegistrationOtpRepository otps;
    @Autowired private RegistrationEventRepository events;
    @Autowired private RegistrationEventService eventService;
    @Autowired private RegistrationMailSender mailSender;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private TopicExchange pokerEventsExchange;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private GenericContainer<?> mailpitContainer;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void otpAndEventRollbackWithAccountChanges() {
        long before = accounts.count();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Account account = accounts.saveAndFlush(newAccount());
            otps.saveAndFlush(RegistrationOtp.builder().account(account).codeHash("hashed-otp")
                    .expiresAt(Instant.now().plusSeconds(300)).lastSentAt(Instant.now()).build());
            events.saveAndFlush(RegistrationEvent.builder().accountId(account.getId()).occurredAt(Instant.now()).build());
            status.setRollbackOnly();
        });
        assertThat(accounts.count()).isEqualTo(before);
        assertThat(otps.count()).isZero();
    }

    @Test
    void smtpMailArrivesInDisposableMailpit() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mailSender.sendOtp(email, "012345", Duration.ofMinutes(5));
        try (HttpClient client = HttpClient.newHttpClient()) {
            URI messagesUri = URI.create("http://" + mailpitContainer.getHost() + ":"
                    + mailpitContainer.getMappedPort(8025) + "/api/v1/messages");
            String body = client.send(HttpRequest.newBuilder(messagesUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            JsonNode messages = jsonMapper.readTree(body).get("messages");
            JsonNode received = null;
            for (JsonNode message : messages) {
                if (message.get("To").get(0).get("Address").asString().equals(email)) {
                    received = message;
                    break;
                }
            }
            assertThat(received).isNotNull();
            URI messageUri = URI.create(messagesUri.toString().replace("/messages", "/message/")
                    + received.get("ID").asString());
            String detail = client.send(HttpRequest.newBuilder(messageUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertThat(jsonMapper.readTree(detail).get("Text").asString()).contains("012345", "300");
        }
    }

    @Test
    void smtpFailureHasStableErrorWithoutLeakingTheCode() {
        JavaMailSender brokenSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("raw OTP must not be exposed"))
                .when(brokenSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        RegistrationMailSender sender = new RegistrationMailSender(brokenSender,
                new com.msspoker.authservice.shared.infrastructure.config.AuthMailProperties("test@poker.local"));
        assertThatThrownBy(() -> sender.sendOtp("test@example.com", "012345", Duration.ofMinutes(5)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_MAIL_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain("012345", "raw OTP");
                });
    }

    @Test
    void unroutableEventRemainsPendingThenRetriesWithTheSameId() {
        Account account = accounts.saveAndFlush(newAccount());
        RegistrationEvent event = events.saveAndFlush(RegistrationEvent.builder()
                .accountId(account.getId()).occurredAt(Instant.now()).build());
        eventService.publishPendingEvents();
        RegistrationEvent pending = events.findById(event.getEventId()).orElseThrow();
        assertThat(pending.getPublishedAt()).isNull();
        assertThat(pending.getDeliveryAttempts()).isEqualTo(1);

        Queue queue = new Queue("auth-test." + UUID.randomUUID(), false, false, false);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(pokerEventsExchange)
                .with(AccountRegisteredEvent.EVENT_TYPE));
        try {
            eventService.publishPendingEvents();
            Message received = rabbit.receive(queue.getName(), 5000);
            assertThat(received).isNotNull();
            JsonNode envelope = jsonMapper.readTree(received.getBody());
            assertThat(envelope.get("eventId").asString()).isEqualTo(event.getEventId().toString());
            assertThat(envelope.get("eventType").asString()).isEqualTo(AccountRegisteredEvent.EVENT_TYPE);
            assertThat(envelope.get("occurredAt").asString()).isEqualTo(pending.getOccurredAt().toString());
            assertThat(envelope.get("payload").get("accountId").asString()).isEqualTo(account.getId().toString());
            RegistrationEvent delivered = events.findById(event.getEventId()).orElseThrow();
            assertThat(delivered.getPublishedAt()).isNotNull();
            assertThat(delivered.getDeliveryAttempts()).isEqualTo(2);
            eventService.publishPendingEvents();
            assertThat(rabbit.receive(queue.getName(), 100)).isNull();
        } finally {
            rabbitAdmin.deleteQueue(queue.getName());
        }
    }

    private Account newAccount() {
        return Account.builder().email(UUID.randomUUID() + "@example.com").passwordHash("test-fixture").build();
    }
}
