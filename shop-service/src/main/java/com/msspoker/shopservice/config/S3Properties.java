package com.msspoker.shopservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "s3")
@Getter
@Setter
public class S3Properties {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "admin";
    private String secretKey = "poker123456";
    private String bucket = "poker-assets";
    private String publicUrl = "http://localhost:9000/poker-assets";
}
