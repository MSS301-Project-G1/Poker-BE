package com.msspoker.gameservice.websocket;

import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.enums.GameError;
import com.msspoker.common.security.SessionAccessClient;
import com.msspoker.common.security.SessionAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;

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
    private final SessionAccessClient authority;
    private record Credentials(String token, String tabId) {}
    private final Map<String, Credentials> credentials = new ConcurrentHashMap<>();
    private final java.util.Set<String> developmentSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void remove(String id) {
        sessions.remove(id);
        credentials.remove(id);
        developmentSessions.remove(id);
    }

    public void authorize(String sessionId, String token, String tabId, boolean development) {
        if (development) { developmentSessions.add(sessionId); return; }
        authority.verify(token, tabId);
        credentials.put(sessionId, new Credentials(token, tabId));
    }

    public void verify(String sessionId) {
        var current = credentials.get(sessionId);
        if (current != null) authority.verify(current.token(), current.tabId());
        else if (!developmentSessions.contains(sessionId)) throw new SessionAccessException("AUTH_UNAUTHENTICATED", 401);
    }

    @Scheduled(fixedDelay = 1000, scheduler = "sessionAuthorityScheduler")
    public void checkActiveSessions() {
        credentials.forEach((id, current) -> {
            try {
                authority.verify(current.token(), current.tabId());
            } catch (SessionAccessException exception) {
                // An authority outage blocks commands, but must not be presented as a login elsewhere.
                if (exception.getStatus() != 503) reject(id, sessionError(exception));
            }
        });
    }

    public GameApiException sessionError(SessionAccessException exception) {
        GameError code = switch (exception.getCode()) {
            case "AUTH_SESSION_REPLACED" -> GameError.GAME_SESSION_REPLACED;
            case "AUTH_TAB_REPLACED" -> GameError.GAME_TAB_REPLACED;
            case "AUTH_ACCOUNT_BANNED" -> GameError.GAME_ACCOUNT_BANNED;
            case "AUTH_SESSION_UNAVAILABLE" -> GameError.GAME_DEPENDENCY_UNAVAILABLE;
            default -> GameError.GAME_UNAUTHORIZED;
        };
        String message = switch (code) {
            case GAME_SESSION_REPLACED -> "Tài khoản đã đăng nhập ở nơi khác.";
            case GAME_TAB_REPLACED -> "Bạn đã chuyển sang tab khác. Bạn có thể đóng tab này.";
            case GAME_ACCOUNT_BANNED -> "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.";
            default -> "Chưa xác thực được phiên chơi.";
        };
        return new GameApiException(code, HttpStatus.valueOf(exception.getStatus()), message);
    }

    public void reject(String sessionId, GameApiException ex) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) { credentials.remove(sessionId); return; }
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
        } finally {
            remove(sessionId);
        }
    }
}
