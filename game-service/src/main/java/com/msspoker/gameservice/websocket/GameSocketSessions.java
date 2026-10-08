package com.msspoker.gameservice.websocket;

import com.msspoker.gameservice.exception.GameApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSocketSessions {
    private final GameStompErrorHandler errors;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void remove(String id) {
        sessions.remove(id);
    }

    public void reject(String sessionId, GameApiException ex) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) throw ex;
        try {
            var error = errors.handleClientMessageProcessingError(null, ex);
            session.sendMessage(new TextMessage(new StompEncoder().encode(error.getHeaders(), error.getPayload())));
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception failure) {
            log.warn("Could not send rejection to game socket {}", sessionId, failure);
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception closeFailure) {
                log.debug("Game socket close failed", closeFailure);
            }
        }
    }
}
