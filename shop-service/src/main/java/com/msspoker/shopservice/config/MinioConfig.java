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
@RequiredArgsConstructor
@Slf4j
public class MinioConfig {

    private final S3Properties s3Properties;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(s3Properties.getEndpoint())
                    .credentials(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                    .build();

            initializeBucketQuietly(client, s3Properties.getBucket());
            return client;
        } catch (Exception e) {
            log.warn("⚠️ MinIO bucket initialization deferred: {}", e.getMessage());
            return MinioClient.builder()
                    .endpoint(s3Properties.getEndpoint())
                    .credentials(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                    .build();
        }
    }

    private void initializeBucketQuietly(MinioClient client, String bucketName) {
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
