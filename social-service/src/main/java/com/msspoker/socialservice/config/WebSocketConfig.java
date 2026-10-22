package com.msspoker.socialservice.config;

import com.msspoker.socialservice.websocket.SocketSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  private final SocketSecurity security;
  private final String[] origins;
  private final int size;

  public WebSocketConfig(
      SocketSecurity security,
      @Value("${social.ws.allowed-origins}") String[] origins,
      @Value("${social.ws.message-size:8192}") int size) {
    this.security = security;
    this.origins = origins;
    this.size = size;
  }

  @Bean
  public ThreadPoolTaskScheduler heartbeatScheduler() {
    var s = new ThreadPoolTaskScheduler();
    s.setPoolSize(1);
    s.setThreadNamePrefix("social-heartbeat-");
    return s;
  }

  public void registerStompEndpoints(StompEndpointRegistry r) {
    r.setPreserveReceiveOrder(true);
    r.addEndpoint("/ws/social").setAllowedOrigins(origins);
  }

  public void configureMessageBroker(MessageBrokerRegistry r) {
    r.setApplicationDestinationPrefixes("/app");
    r.setUserDestinationPrefix("/user");
    r.enableSimpleBroker("/queue")
        .setHeartbeatValue(new long[] {10000, 10000})
        .setTaskScheduler(heartbeatScheduler());
    r.setPreservePublishOrder(true);
  }

  public void configureClientInboundChannel(ChannelRegistration r) {
    r.interceptors(security);
  }

  public void configureWebSocketTransport(WebSocketTransportRegistration r) {
    r.setMessageSizeLimit(size)
        .setSendBufferSizeLimit(65536)
        .setSendTimeLimit(10000)
        .setTimeToFirstMessage(10000);
    r.addDecoratorFactory(
        h ->
            new WebSocketHandlerDecorator(h) {
              public void afterConnectionEstablished(WebSocketSession s) throws Exception {
                security.register(s);
                super.afterConnectionEstablished(s);
              }

              public void afterConnectionClosed(WebSocketSession s, CloseStatus c)
                  throws Exception {
                security.remove(s.getId());
                super.afterConnectionClosed(s, c);
              }
            });
  }
}
