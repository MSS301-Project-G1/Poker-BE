package com.msspoker.authservice.shared.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CheckinProperties.class, WalletProperties.class})
public class CheckinConfig {
}
