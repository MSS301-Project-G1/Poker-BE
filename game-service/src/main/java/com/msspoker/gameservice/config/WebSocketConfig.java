package com.msspoker.gameservice.config;

import com.msspoker.gameservice.constant.GameSocketConstants;
import com.msspoker.gameservice.websocket.GameChannelInterceptor;
import com.msspoker.gameservice.websocket.GameSocketSessions;
import com.msspoker.gameservice.websocket.GameStompErrorHandler;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.messaging.simp.config.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final GameChannelInterceptor interceptor;
    private final GameProperties properties;
    private final GameStompErrorHandler errors;
    private final GameSocketSessions sessions;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        registry.setErrorHandler(errors);
        registry.addEndpoint(GameSocketConstants.ENDPOINT).setAllowedOrigins(properties.getAllowedOrigins());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(GameSocketConstants.APP_PREFIX);
        registry.enableSimpleBroker(GameSocketConstants.BROKER_PREFIX);
        registry.setUserDestinationPrefix(GameSocketConstants.USER_PREFIX);
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor);
        registration.executor(stompChannelExecutor());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.executor(stompChannelExecutor());
    }

    /**
     * STOMP must not share the bounded taskScheduler: tick() publishes while holding a table lock, and inbound
     * handlers wait for that lock, so both sides starved each other's slots and froze the table.
     * Unbounded on purpose: preserve receive/publish order already allows one in-flight message per session.
     */
    @Bean(destroyMethod = "close")
    public SimpleAsyncTaskExecutor stompChannelExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(GameSocketConstants.CHANNEL_THREAD_PREFIX);
        executor.setVirtualThreads(true);
        return executor;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.register(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
        registration.setMessageSizeLimit(GameSocketConstants.MESSAGE_SIZE_LIMIT_BYTES);
        registration.setSendBufferSizeLimit(GameSocketConstants.SEND_BUFFER_SIZE_LIMIT_BYTES);
        registration.setSendTimeLimit(GameSocketConstants.SEND_TIME_LIMIT_MILLIS);
    }
}
