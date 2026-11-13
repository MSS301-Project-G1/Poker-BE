package com.msspoker.chatservice.websocket;

import com.msspoker.chatservice.config.ChatProperties;
import com.msspoker.common.security.SessionAccessClient;
import com.msspoker.common.security.SessionAccessException;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocketSecurity implements ChannelInterceptor {
    private final JwtDecoder decoder;
    private final SessionAccessClient authority;

    private record Credentials(String token, String tabId) {}

    private final Map<String, Credentials> credentials = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxRate;
    private final Map<String, Instant> expiry = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    private final Map<String, long[]> rates = new ConcurrentHashMap<>();

    public SocketSecurity(ChatProperties properties, Clock clock, SessionAccessClient authority) {
        String jwks = properties.getJwt().getJwkSetUri();
        String issuer = properties.getJwt().getIssuer();
        String audience = properties.getJwt().getAudience();
        this.authority = authority;
        var jwt =
                NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256).build();
        jwt.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(Duration.ZERO),
                        new JwtIssuerValidator(issuer),
                        new JwtClaimValidator<List<String>>(
                                "aud", a -> a != null && a.contains(audience)),
                        new JwtClaimValidator<Instant>("exp", Objects::nonNull)));
        decoder = jwt;
        this.clock = clock;
        this.maxRate = properties.getWs().getMessagesPerSecond();
    }

    public void register(WebSocketSession s) {
        sockets.put(s.getId(), s);
    }

    public void remove(String id) {
        sockets.remove(id);
        expiry.remove(id);
        credentials.remove(id);
        rates.remove(id);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent e) {
        remove(e.getSessionId());
    }

    @Scheduled(fixedDelay = 1000)
    public void expireSessions() {
        expiry.forEach(
                (id, time) -> {
                    if (!clock.instant().isBefore(time)) {
                        reject(id);
                        return;
                    }
                    var current = credentials.get(id);
                    if (current != null) {
                        try {
                            authority.verify(current.token(), current.tabId());
                        } catch (SessionAccessException exception) {
                            if (exception.getStatus() != 503) reject(id);
                        }
                    }
                });
    }

    private void reject(String id) {
        var s = sockets.get(id);
        try {
            if (s != null) s.close(CloseStatus.POLICY_VIOLATION);
        } catch (java.io.IOException ignored) {
        } finally {
            remove(id);
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var a = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (a == null || a.getCommand() == null) return message;
        try {
            if (a.getCommand() == StompCommand.CONNECT) {
                String auth = a.getFirstNativeHeader("Authorization");
                if (auth == null || !auth.startsWith("Bearer "))
                    throw new IllegalArgumentException();
                Jwt token = decoder.decode(auth.substring(7));
                authority.verify(auth.substring(7), a.getFirstNativeHeader("X-Tab-Id"));
                credentials.put(
                        a.getSessionId(),
                        new Credentials(auth.substring(7), a.getFirstNativeHeader("X-Tab-Id")));
                String name = UUID.fromString(token.getSubject()).toString();
                if (!name.equals(token.getSubject())) throw new IllegalArgumentException();
                a.setUser(() -> name);
                expiry.put(a.getSessionId(), token.getExpiresAt());
            } else if (a.getCommand() != StompCommand.DISCONNECT) {
                var current = credentials.get(a.getSessionId());
                if (current == null) throw new IllegalArgumentException();
                authority.verify(current.token(), current.tabId());
                Instant end = expiry.get(a.getSessionId());
                if (a.getUser() == null || end == null || !clock.instant().isBefore(end))
                    throw new IllegalArgumentException();
                if (a.getCommand() == StompCommand.SUBSCRIBE) {
                    if (!Set.of("/user/queue/chat", "/user/queue/chat-errors")
                            .contains(a.getDestination())) throw new IllegalArgumentException();
                } else if (a.getCommand() == StompCommand.SEND) {
                    if (!(a.getDestination() != null
                            && a.getDestination()
                                    .matches("^/app/conversations/[0-9a-fA-F-]{36}/send$")))
                        throw new IllegalArgumentException();
                    long second = clock.instant().getEpochSecond();
                    long[] rate =
                            rates.computeIfAbsent(a.getSessionId(), id -> new long[] {second, 0});
                    synchronized (rate) {
                        if (rate[0] != second) {
                            rate[0] = second;
                            rate[1] = 0;
                        }
                        if (++rate[1] > maxRate) throw new IllegalArgumentException();
                    }
                } else if (a.getCommand() != StompCommand.UNSUBSCRIBE)
                    throw new IllegalArgumentException();
            }
            return message;
        } catch (RuntimeException e) {
            reject(a.getSessionId());
            // The socket is closed explicitly; throwing here would make Spring log native headers
            // (JWT).
            return null;
        }
    }
}
