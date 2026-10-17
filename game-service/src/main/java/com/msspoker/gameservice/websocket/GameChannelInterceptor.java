package com.msspoker.gameservice.websocket;

import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.security.GameIdentity;
import com.msspoker.gameservice.service.TableRegistry;
import com.msspoker.gameservice.validator.TableMembershipValidator;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GameChannelInterceptor implements ChannelInterceptor {
    private static final Pattern SEND = Pattern.compile("^/app/tables/([0-9a-fA-F-]{36})/(action|sync)$");
    private static final Pattern SUBSCRIBE = Pattern.compile("^/user/queue/tables/([0-9a-fA-F-]{36})$");
    private final GameIdentity identity;
    private final TableRegistry registry;
    private final TableMembershipValidator membership;
    private final GameSocketSessions sessions;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        StompCommand command = accessor.getCommand();
        try {
        if (command == StompCommand.CONNECT) {
            accessor.setUser(identity.authenticate(accessor.getFirstNativeHeader("Authorization"),
                    accessor.getFirstNativeHeader("devUser")));
        } else if (command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) {
            Principal user = accessor.getUser();
            if (user == null) throw GameExceptions.unauthorized();
            String destination = accessor.getDestination();
            if (command == StompCommand.SUBSCRIBE && "/user/queue/game-errors".equals(destination)) return message;
            var matcher = (command == StompCommand.SEND ? SEND : SUBSCRIBE).matcher(destination == null ? "" : destination);
            if (!matcher.matches()) throw GameExceptions.forbidden();
            membership.validate(registry.require(UUID.fromString(matcher.group(1))), UUID.fromString(user.getName()));
        }
        return message;
        } catch (GameApiException ex) {
            // Ordered inbound delivery can swallow a channel exception; reject on the actual socket.
            sessions.reject(accessor.getSessionId(), ex);
            return null;
        } catch (IllegalArgumentException ex) {
            sessions.reject(accessor.getSessionId(), GameExceptions.invalidRequest());
            return null;
        }
    }
}
