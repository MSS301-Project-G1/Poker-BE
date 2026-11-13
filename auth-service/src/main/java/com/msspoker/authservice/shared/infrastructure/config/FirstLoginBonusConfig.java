package com.msspoker.authservice.shared.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FirstLoginBonusProperties.class)
public class FirstLoginBonusConfig {
}
