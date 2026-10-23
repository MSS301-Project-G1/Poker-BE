package com.msspoker.gameservice.websocket;

import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.mapper.ErrorMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class GameStompErrorHandler extends StompSubProtocolErrorHandler {
    private final JsonMapper json;
    private final ErrorMapper errors;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && !(cause instanceof GameApiException)) cause = cause.getCause();
        GameApiException error = cause instanceof GameApiException business ? business : GameExceptions.invalidRequest();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(error.getMessage());
        accessor.setNativeHeader(StompHeaderAccessor.STOMP_CONTENT_TYPE_HEADER, MediaType.APPLICATION_JSON_VALUE);
        byte[] body = json.writeValueAsString(errors.error(error, null)).getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.createMessage(body, accessor.getMessageHeaders());
    }
}
