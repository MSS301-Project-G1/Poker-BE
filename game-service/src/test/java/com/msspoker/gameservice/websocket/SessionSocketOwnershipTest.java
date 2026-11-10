package com.msspoker.gameservice.websocket;

import com.msspoker.common.security.SessionAccessClient;
import com.msspoker.common.security.SessionAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionSocketOwnershipTest {
    @Test
    void accountBanIsReportedAsABanInsteadOfAnExpiredOrReplacedLogin() {
        var sessions = new GameSocketSessions(mock(GameStompErrorHandler.class), mock(SessionAccessClient.class));
        var error = sessions.sessionError(new SessionAccessException("AUTH_ACCOUNT_BANNED", 403));
        assertThat(error.getCode()).isEqualTo(com.msspoker.gameservice.enums.GameError.GAME_ACCOUNT_BANNED);
        assertThat(error.getStatus().value()).isEqualTo(403);
    }

    @Test
    void replacementBlocksCommandsAndClosesOldSocketWithoutLeavingTheTable() throws Exception {
        var authority = mock(SessionAccessClient.class);
        var errors = mock(GameStompErrorHandler.class);
        var frame = StompHeaderAccessor.create(StompCommand.ERROR);
        when(errors.handleClientMessageProcessingError(any(), any()))
                .thenReturn(MessageBuilder.createMessage(new byte[0], frame.getMessageHeaders()));
        var sessions = new GameSocketSessions(errors, authority);
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("old-socket");
        sessions.register(socket);
        sessions.authorize("old-socket", "signed-jwt", "tab-a", false);
        doThrow(new SessionAccessException("AUTH_TAB_REPLACED", 409)).when(authority).verify("signed-jwt", "tab-a");
        assertThatThrownBy(() -> sessions.verify("old-socket")).isInstanceOf(SessionAccessException.class);
        sessions.checkActiveSessions();
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        assertThatThrownBy(() -> sessions.verify("old-socket")).isInstanceOf(SessionAccessException.class);
    }

    @Test
    void authorityOutageBlocksCommandsButDoesNotPretendAnotherDeviceLoggedIn() throws Exception {
        var authority = mock(SessionAccessClient.class);
        var sessions = new GameSocketSessions(mock(GameStompErrorHandler.class), authority);
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("socket");
        sessions.register(socket);
        sessions.authorize("socket", "jwt", "tab", false);
        doThrow(new SessionAccessException("AUTH_SESSION_UNAVAILABLE", 503)).when(authority).verify("jwt", "tab");
        sessions.checkActiveSessions();
        verify(socket, never()).close(any());
        assertThatThrownBy(() -> sessions.verify("socket")).isInstanceOf(SessionAccessException.class);
    }
}
