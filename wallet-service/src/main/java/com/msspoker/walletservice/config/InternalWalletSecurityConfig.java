package com.msspoker.walletservice.config;

import com.msspoker.walletservice.security.InternalWalletFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class InternalWalletSecurityConfig {
    @Bean
    public FilterRegistrationBean<InternalWalletFilter> internalWalletFilter(@Value("${wallet.internal-key:}") String key) {
        var registration = new FilterRegistrationBean<>(new InternalWalletFilter(key));
        registration.addUrlPatterns("/internal", "/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
