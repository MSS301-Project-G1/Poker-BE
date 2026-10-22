package com.msspoker.chatservice.websocket;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.exception.ServiceException;
import com.msspoker.chatservice.service.ChatService;
import com.msspoker.common.response.ErrorResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatSocketController {
  private final ChatService chat;
  private final SimpMessagingTemplate socket;

  @MessageMapping("/conversations/{id}/send")
  public void send(
      Principal user, @DestinationVariable UUID id, @Valid SendMessageRequest request) {
    chat.send(UUID.fromString(user.getName()), id, request);
  }

  @MessageExceptionHandler
  public void error(Exception e, Principal user) {
    if (user != null) {
      String code = e instanceof ServiceException s ? s.code : "CHAT_INVALID_REQUEST";
      String message = e instanceof ServiceException ? e.getMessage() : "Tin nhắn không hợp lệ.";
      socket.convertAndSendToUser(
          user.getName(),
          "/queue/chat-errors",
          new ErrorResponse(code, message, Instant.now(), null));
    }
  }
}
