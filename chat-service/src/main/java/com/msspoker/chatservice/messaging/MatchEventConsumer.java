package com.msspoker.chatservice.messaging;

import com.msspoker.chatservice.repository.EventReceiptRepository;
import com.msspoker.chatservice.service.MatchLifecycleService;
import com.msspoker.common.event.EventEnvelope;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class MatchEventConsumer {
  private final JsonMapper json;
  private final EventReceiptRepository receipts;
  private final MatchLifecycleService lifecycle;

  @RabbitListener(queues = "chat-service.match.started")
  @Transactional
  public void started(Message m) {
    var e = parse(m, "match.started");
    var p = e.payload();
    UUID match = id(p, "matchId");
    id(p, "tableId");
    id(p, "sourceId");
    mode(p);
    var values = p.path("playerIds");
    if (!values.isArray() || values.size() < 2 || values.size() > 100)
      throw new IllegalArgumentException("Invalid playerIds");
    List<UUID> players = new ArrayList<>();
    values.forEach(n -> players.add(UUID.fromString(n.asString())));
    if (new HashSet<>(players).size() != players.size())
      throw new IllegalArgumentException("Duplicate playerIds");
    if (receipts.claim(e.eventId(), e.eventType())) lifecycle.started(match, players);
  }

  @RabbitListener(queues = "chat-service.match.finished")
  @Transactional
  public void finished(Message m) {
    var e = parse(m, "match.finished");
    var p = e.payload();
    UUID match = id(p, "matchId");
    id(p, "tableId");
    id(p, "sourceId");
    mode(p);
    Instant at = Instant.parse(p.path("finishedAt").asString());
    if (receipts.claim(e.eventId(), e.eventType())) lifecycle.finished(match, at);
  }

  private UUID id(JsonNode n, String field) {
    if (!n.path(field).isString()) throw new IllegalArgumentException("Missing " + field);
    return UUID.fromString(n.path(field).asString());
  }

  private void mode(JsonNode n) {
    if (!Set.of("NORMAL", "RANK", "CUSTOM", "TOURNAMENT").contains(n.path("mode").asString()))
      throw new IllegalArgumentException("Invalid mode");
  }

  private EventEnvelope<JsonNode> parse(Message m, String type) {
    if (m.getBody().length > 65536) throw new IllegalArgumentException("Event too large");
    var e = json.readValue(m.getBody(), new TypeReference<EventEnvelope<JsonNode>>() {});
    if (e.eventId() == null
        || e.occurredAt() == null
        || !type.equals(e.eventType())
        || e.payload() == null
        || !e.payload().isObject()) throw new IllegalArgumentException("Invalid envelope");
    return e;
  }
}
