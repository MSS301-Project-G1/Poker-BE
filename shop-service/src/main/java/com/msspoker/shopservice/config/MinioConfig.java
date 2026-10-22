package com.msspoker.shopservice.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinioConfig {

    private final S3Properties s3Properties;

    public MinioConfig(S3Properties s3Properties) {
        this.s3Properties = s3Properties;
    }

    @Bean
    public MinioClient minioClient() {
        String endpoint = (s3Properties.getEndpoint() != null && !s3Properties.getEndpoint().isBlank())
                ? s3Properties.getEndpoint()
                : "http://localhost:9000";
        String accessKey = s3Properties.getAccessKey();
        String secretKey = s3Properties.getSecretKey();

        boolean hasCredentials = accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();

        MinioClient.Builder builder = MinioClient.builder().endpoint(endpoint);
        if (s3Properties.getRegion() != null && !s3Properties.getRegion().isBlank()) {
            builder.region(s3Properties.getRegion());
        }
        if (hasCredentials) {
            builder.credentials(accessKey, secretKey);
        }
        MinioClient client = builder.build();

        if (hasCredentials && s3Properties.getBucket() != null && !s3Properties.getBucket().isBlank()) {
            initializeBucketQuietly(client, s3Properties.getBucket());
        }

        return client;
    }

    private void initializeBucketQuietly(MinioClient client, String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            return;
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket '{}'", bucketName);

                String policy = """
                        {
                          "Version": "2012-10-17",
                          "Statement": [
                            {
                              "Effect": "Allow",
                              "Principal": {"AWS": ["*"]},
                              "Action": ["s3:GetObject"],
                              "Resource": ["arn:aws:s3:::%s/*"]
                            }
                          ]
                        }
                        """.formatted(bucketName);
                client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
                log.info("Set public-read policy for MinIO bucket '{}'", bucketName);
            }
        } catch (Exception e) {
            log.warn("⚠️ MinIO bucket check skipped at startup: {}", e.getMessage());
        }
    }
}
