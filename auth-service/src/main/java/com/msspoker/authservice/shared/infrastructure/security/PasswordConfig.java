package com.msspoker.authservice.shared.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        // Stores the algorithm prefix (currently {bcrypt}) for future hash upgrades.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
