package com.msspoker.shopservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Slf4j
@Configuration
public class PayOSConfig {

    @Value("${payos.client-id:}")
    private String clientId;

    @Value("${payos.api-key:}")
    private String apiKey;

    @Value("${payos.checksum-key:}")
    private String checksumKey;

    @Bean
    public PayOS payOS() {
        String safeClientId = (clientId != null && !clientId.isBlank()) ? clientId : "dummy-client-id";
        String safeApiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : "dummy-api-key";
        String safeChecksumKey = (checksumKey != null && !checksumKey.isBlank()) ? checksumKey : "dummy-checksum-key";

        if ("dummy-client-id".equals(safeClientId)) {
            log.warn(" PayOS credentials are NOT configured. Payment creation and webhook verification will fail until valid credentials are provided in .env.");
        } else {
            String masked = safeClientId.length() > 8
                    ? safeClientId.substring(0, 4) + "***" + safeClientId.substring(safeClientId.length() - 4)
                    : "***";
            log.info(" PayOS SDK initialized successfully (clientId={})", masked);
        }


        return new PayOS(safeClientId, safeApiKey, safeChecksumKey);
    }
}
