package com.msspoker.shopservice.service.impl;

import com.github.f4b6a3.uuid.UuidCreator;
import com.msspoker.shopservice.config.S3Properties;
import com.msspoker.shopservice.constant.StorageKeyConstants;
import com.msspoker.shopservice.dto.response.UploadedAssetResponse;
import com.msspoker.shopservice.exception.CardSkinExceptions;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.service.S3StorageService;
import io.minio.GetObjectArgs;
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
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MinioStorageServiceImpl implements S3StorageService {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{1,10}$");

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
                            .headers(Map.of(HttpHeaders.CACHE_CONTROL, StorageKeyConstants.CACHE_CONTROL))
                            .build()
            );
            return getPublicUrl(objectKey);
        } catch (Exception e) {
            log.error("Failed to upload file to S3 key={}: {}", objectKey, e.getMessage(), e);
            throw CardSkinExceptions.s3UploadError("Không thể tải tệp lên S3: " + e.getMessage());
        }
    }

    @Override
    public UploadedAssetResponse uploadAsset(String folder, MultipartFile file) {
        if (!StorageKeyConstants.UPLOAD_FOLDERS.contains(folder)) {
            throw ProductExceptions.invalidUploadFolder();
        }
        if (file.isEmpty()) {
            throw ProductExceptions.emptyUploadFile();
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded asset {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw CardSkinExceptions.s3UploadError("Không thể đọc tệp tải lên.");
        }
        String key = StorageKeyConstants.UPLOADS + folder + "/" + UuidCreator.getTimeOrderedEpoch()
                + extensionOf(file.getOriginalFilename());
        return new UploadedAssetResponse(uploadFile(key, content, file.getContentType()), key);
    }

    /** The name's extension when it is a plain one like ".png", else none, so a file name cannot steer the key. */
    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        Matcher matcher = SAFE_EXTENSION.matcher(filename);
        return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : "";
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

    /**
     * The plain link, which is what products store. A presigned link would stop working once it expires;
     * AssetUrlSigner signs links in responses for as long as the bucket folder stays private.
     */
    @Override
    public String getPublicUrl(String objectKey) {
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
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
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
