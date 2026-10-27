package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.config.S3Properties;
import com.msspoker.shopservice.exception.CardSkinExceptions;
import com.msspoker.shopservice.service.S3StorageService;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class MinioStorageServiceImpl implements S3StorageService {

    private final MinioClient minioClient;
    private final S3Properties s3Properties;

    // Thread pool dành riêng cho song song hóa upload assets S3
    private final ExecutorService uploadExecutor = Executors.newFixedThreadPool(12);

    public MinioStorageServiceImpl(MinioClient minioClient, S3Properties s3Properties) {
        this.minioClient = minioClient;
        this.s3Properties = s3Properties;
    }

    @Override
    public String uploadFile(String objectKey, byte[] content, String contentType) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(s3Properties.getBucket())
                            .object(objectKey)
                            .stream(stream, content.length, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            return getPublicUrl(objectKey);
        } catch (Exception e) {
            log.error("Failed to upload file to S3 key={}: {}", objectKey, e.getMessage(), e);
            throw CardSkinExceptions.s3UploadError("Không thể tải tệp lên S3: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> uploadBatch(Map<String, byte[]> files, String contentType) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }

        Map<String, String> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String key = entry.getKey();
            byte[] bytes = entry.getValue();

            String resolvedType = resolveContentType(key, contentType);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String url = uploadFile(key, bytes, resolvedType);
                results.put(key, url);
            }, uploadExecutor);

            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("Batch upload to S3 failed: {}", e.getMessage(), e);
            throw CardSkinExceptions.s3UploadError("Lỗi trong quá trình tải lên song song các lá bài: " + e.getMessage());
        }

        return results;
    }

    @Override
    public void deleteFolder(String folderPrefix) {
        if (folderPrefix == null || folderPrefix.isBlank()) {
            return;
        }

        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(s3Properties.getBucket())
                            .prefix(folderPrefix)
                            .recursive(true)
                            .build()
            );

            List<DeleteObject> toDelete = new LinkedList<>();
            for (Result<Item> result : objects) {
                Item item = result.get();
                toDelete.add(new DeleteObject(item.objectName()));
            }

            if (!toDelete.isEmpty()) {
                Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                        RemoveObjectsArgs.builder()
                                .bucket(s3Properties.getBucket())
                                .objects(toDelete)
                                .build()
                );
                for (Result<DeleteError> res : results) {
                    DeleteError error = res.get();
                    log.warn("Error deleting object {} from S3: {}", error.objectName(), error.message());
                }
                log.info("Deleted {} old objects in S3 prefix={}", toDelete.size(), folderPrefix);
            }
        } catch (Exception e) {
            log.warn("Failed to clean up old S3 folder {}: {}", folderPrefix, e.getMessage());
            // Warning only, do not crash the operation
        }
    }

    @Override
    public String getPublicUrl(String objectKey) {
        if (s3Properties.getEndpoint() != null && s3Properties.getEndpoint().contains("amazonaws.com")) {
            try {
                return minioClient.getPresignedObjectUrl(
                        io.minio.GetPresignedObjectUrlArgs.builder()
                                .method(io.minio.http.Method.GET)
                                .bucket(s3Properties.getBucket())
                                .object(objectKey)
                                .expiry(7, java.util.concurrent.TimeUnit.DAYS)
                                .build()
                );
            } catch (Exception e) {
                log.warn("Failed to generate presigned S3 URL for key {}: {}", objectKey, e.getMessage());
            }
        }
        String base = s3Properties.getPublicUrl();
        if (base == null) {
            base = "";
        }
        if (!base.endsWith("/")) {
            base += "/";
        }
        return base + objectKey;
    }

    @Override
    public byte[] downloadFile(String objectKey) {
        try (java.io.InputStream stream = minioClient.getObject(
                io.minio.GetObjectArgs.builder()
                        .bucket(s3Properties.getBucket())
                        .object(objectKey)
                        .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download file from S3 key={}: {}", objectKey, e.getMessage(), e);
            throw CardSkinExceptions.s3UploadError("Không thể tải tệp từ S3: " + e.getMessage());
        }
    }

    private String resolveContentType(String key, String fallback) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".py")) return "text/x-python";
        return fallback != null ? fallback : "application/octet-stream";
    }
}
