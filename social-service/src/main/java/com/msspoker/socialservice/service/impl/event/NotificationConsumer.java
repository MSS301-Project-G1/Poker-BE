package com.msspoker.socialservice.service.impl.event;

import com.msspoker.common.event.EventEnvelope;
import com.msspoker.socialservice.dto.event.Notification;
import com.msspoker.socialservice.repository.EventReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {
  private final JsonMapper json;
  private final EventReceiptRepository receipts;
  private final ApplicationEventPublisher events;

  public record Delivered(EventEnvelope<Notification> event) {}

  @RabbitListener(queues = "social-service.notification.requested")
  @Transactional
  public void receive(Message message) {
    if (message.getBody().length > 65536)
      throw new IllegalArgumentException("Notification too large");
    var e = json.readValue(message.getBody(), new TypeReference<EventEnvelope<Notification>>() {});
    if (e.eventId() == null
        || e.occurredAt() == null
        || !"notification.requested".equals(e.eventType())
        || e.payload() == null) throw new IllegalArgumentException("Invalid envelope");
    e.payload().validate();
    if (receipts.claim(e.eventId(), e.eventType())) events.publishEvent(new Delivered(e));
  }
}
