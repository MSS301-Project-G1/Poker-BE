package com.msspoker.chatservice.service.impl;

import com.msspoker.chatservice.exception.ServiceException;
import com.msspoker.chatservice.service.SocialClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class HttpSocialClient implements SocialClient {
  private final RestClient http;
  private final String key;

  public HttpSocialClient(
      @Value("${chat.social-url}") String url,
      @Value("${chat.social-internal-key:}") String key,
      @Value("${chat.social-timeout:3s}") Duration timeout) {
    var factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    this.key = key;
  }

  public Relation relation(UUID a, UUID b) {
    try {
      if (key.isBlank()) throw new IllegalStateException("Missing credential");
      var body =
          http.get()
              .uri("/internal/friends/{a}/with/{b}", a, b)
              .header("X-Social-Internal-Key", key)
              .retrieve()
              .body(JsonNode.class);
      if (body == null || !body.path("isFriend").isBoolean() || !body.path("isBlocked").isBoolean())
        throw new IllegalStateException("Invalid Social response");
      return new Relation(body.path("isFriend").asBoolean(), body.path("isBlocked").asBoolean());
    } catch (RuntimeException e) {
      throw new ServiceException(
          "DEPENDENCY_UNAVAILABLE", HttpStatus.BAD_GATEWAY, "Không kiểm tra được quan hệ bạn bè.");
    }
  }
}
