package com.msspoker.chatservice.websocket;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.exception.ChatApiException;
import com.msspoker.chatservice.service.MessageService;
import com.msspoker.common.response.ErrorResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/** Nhận STOMP; Principal được xác thực ở lớp bảo mật WebSocket, không lấy actor từ payload. */
@Controller
@RequiredArgsConstructor
public class ChatSocketController {
    private final MessageService messages;
    private final SimpMessagingTemplate socket;

    /** Lấy UUID từ Principal rồi gọi cùng MessageService.send như luồng HTTP. */
    @MessageMapping("/conversations/{id}/send")
    public void send(
            Principal user, @DestinationVariable UUID id, @Valid SendMessageRequest request) {
        messages.send(UUID.fromString(user.getName()), id, request);
    }

    /** Gửi lỗi về queue riêng của người gọi; lỗi ngoài nghiệp vụ dùng thông báo chung. */
    @MessageExceptionHandler
    public void error(Exception e, Principal user) {
        if (user != null) {
            String code =
                    e instanceof ChatApiException s ? s.getCode().name() : "CHAT_INVALID_REQUEST";
            String message =
                    e instanceof ChatApiException ? e.getMessage() : "Tin nhắn không hợp lệ.";
            socket.convertAndSendToUser(
                    user.getName(),
                    "/queue/chat-errors",
                    new ErrorResponse(code, message, Instant.now(), null));
        }
    }
}
