package com.msspoker.authservice.shared.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({S3Properties.class, AuthAvatarProperties.class})
public class S3Config {
    @Bean
    @ConditionalOnProperty(name = "auth.s3.enabled", havingValue = "true")
    S3Client s3Client(S3Properties properties) {
        var builder = S3Client.builder().region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10)).apiCallAttemptTimeout(Duration.ofSeconds(5)).build());
        if (properties.endpoint() != null) builder.endpointOverride(properties.endpoint())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "auth.s3.enabled", havingValue = "true")
    S3Presigner s3Presigner(S3Properties properties) {
        var builder = S3Presigner.builder().region(Region.of(properties.region())).credentialsProvider(credentials(properties));
        if (properties.endpoint() != null) builder.endpointOverride(properties.endpoint())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        return builder.build();
    }

    private StaticCredentialsProvider credentials(S3Properties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
