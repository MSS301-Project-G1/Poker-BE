package com.msspoker.chatservice.controller;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.dto.response.ChatDtos.*;
import com.msspoker.chatservice.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatController {
  private final ChatService chat;

  public record DirectRequest(@NotNull UUID accountId) {}

  @PostMapping("/direct")
  public ConversationView direct(
      @RequestHeader("X-User-Id") UUID actor, @Valid @RequestBody DirectRequest r) {
    return chat.direct(actor, r.accountId());
  }

  @GetMapping
  public PageView<ConversationView> list(
      @RequestHeader("X-User-Id") UUID actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return chat.list(actor, page, size);
  }

  @GetMapping("/{id}")
  public ConversationView get(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) {
    return chat.get(actor, id);
  }

  @GetMapping("/{id}/messages")
  public History history(
      @RequestHeader("X-User-Id") UUID actor,
      @PathVariable UUID id,
      @RequestParam(required = false) String before,
      @RequestParam(defaultValue = "20") int limit) {
    return chat.history(actor, id, before, limit);
  }

  @PostMapping("/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public MessageView send(
      @RequestHeader("X-User-Id") UUID actor,
      @PathVariable UUID id,
      @Valid @RequestBody SendMessageRequest r) {
    return chat.send(actor, id, r);
  }
}
