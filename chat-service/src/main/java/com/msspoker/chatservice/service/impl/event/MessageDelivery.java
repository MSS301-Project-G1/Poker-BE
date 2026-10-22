package com.msspoker.chatservice.service.impl.event;

import com.msspoker.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
public class MessageDelivery {
  private final SimpMessagingTemplate socket;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void deliver(ChatService.MessageCommitted event) {
    event
        .members()
        .forEach(id -> socket.convertAndSendToUser(id.toString(), "/queue/chat", event.message()));
  }
}
