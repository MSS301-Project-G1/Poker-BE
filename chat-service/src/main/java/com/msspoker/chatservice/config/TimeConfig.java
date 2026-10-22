package com.msspoker.chatservice.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class TimeConfig {
  @Bean
  public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
    var s = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
    s.setPoolSize(2);
    return s;
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
