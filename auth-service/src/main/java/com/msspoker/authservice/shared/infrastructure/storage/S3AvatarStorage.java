package com.msspoker.authservice.shared.infrastructure.storage;

import com.msspoker.authservice.dto.profile.request.AvatarPresignRequest;
import com.msspoker.authservice.dto.profile.response.AvatarPresignResponse;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3AvatarStorage {
    private final S3Properties properties;
    private final ObjectProvider<S3Client> clients;
    private final ObjectProvider<S3Presigner> presigners;

    public AvatarPresignResponse presign(UUID accountId, AvatarPresignRequest request) {
        requireEnabled();
        if (request.fileSize() > properties.maxFileSize().toBytes()) throw new ApiException(ErrorCode.AUTH_AVATAR_TOO_LARGE);
        String extension = switch (request.contentType()) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> throw new ApiException(ErrorCode.AUTH_AVATAR_INVALID);
        };
        String key = prefix() + "staging/" + accountId + "/" + UUID.randomUUID() + extension;
        try {
            var signed = presigners.getObject().presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(properties.uploadTtl())
                    .putObjectRequest(builder -> builder.bucket(properties.bucket()).key(key).contentType(request.contentType())).build());
            Map<String, String> headers = new LinkedHashMap<>();
            signed.signedHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("host")) headers.put(name, String.join(",", values));
            });
            return new AvatarPresignResponse(key, signed.url().toString(), "PUT", headers, signed.expiration());
        } catch (SdkException exception) {
            throw new ApiException(ErrorCode.AUTH_AVATAR_STORAGE_UNAVAILABLE);
        }
    }

    public String resolveViewUrl(String value) {
        return isStoredKey(value) ? viewUrl(value) : value;
    }

    public String finalKey(UUID accountId, String stagingKey) {
        String start = prefix() + "staging/" + accountId + "/";
        if (stagingKey == null || !stagingKey.startsWith(start)
                || !stagingKey.substring(start.length()).matches("[0-9a-f-]{36}\\.(png|jpg)")) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return prefix() + "users/" + accountId + "/" + stagingKey.substring(start.length());
    }

    public void validateAndCopy(String stagingKey, String finalKey) {
        requireEnabled();
        try {
            S3Client client = clients.getObject();
            var head = client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(stagingKey).build());
            String type = stagingKey.endsWith(".png") ? "image/png" : "image/jpeg";
            if (!type.equals(head.contentType()) || head.contentLength() == null || head.contentLength() <= 0) {
                throw new ApiException(ErrorCode.AUTH_AVATAR_INVALID);
            }
            if (head.contentLength() > properties.maxFileSize().toBytes()) throw new ApiException(ErrorCode.AUTH_AVATAR_TOO_LARGE);
            try (var object = client.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(stagingKey)
                    .ifMatch(head.eTag()).build())) {
                byte[] bytes = object.readNBytes(Math.toIntExact(properties.maxFileSize().toBytes() + 1));
                if (bytes.length > properties.maxFileSize().toBytes()) {
                    object.abort();
                    throw new ApiException(ErrorCode.AUTH_AVATAR_TOO_LARGE);
                }
                validateImage(bytes, type);
            }
            // Copy the validated version to a key that the upload URL cannot overwrite later.
            String source = URLEncoder.encode(properties.bucket() + "/" + stagingKey, StandardCharsets.UTF_8).replace("+", "%20");
            client.copyObject(CopyObjectRequest.builder().bucket(properties.bucket()).key(finalKey)
                    .copySource(source).copySourceIfMatch(head.eTag()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) throw new ApiException(ErrorCode.AUTH_AVATAR_UPLOAD_NOT_FOUND);
            if (exception.statusCode() == 412) throw new ApiException(ErrorCode.AUTH_AVATAR_INVALID);
            throw new ApiException(ErrorCode.AUTH_AVATAR_STORAGE_UNAVAILABLE);
        } catch (SdkException | IOException exception) {
            throw new ApiException(ErrorCode.AUTH_AVATAR_STORAGE_UNAVAILABLE);
        }
    }

    public String viewUrl(String key) {
        requireEnabled();
        try {
            return presigners.getObject().presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(properties.viewTtl())
                    .getObjectRequest(builder -> builder.bucket(properties.bucket()).key(key)).build()).url().toString();
        } catch (SdkException exception) {
            throw new ApiException(ErrorCode.AUTH_AVATAR_STORAGE_UNAVAILABLE);
        }
    }

    public boolean isStoredKey(String value) {
        return value != null && Pattern.matches(Pattern.quote(prefix() + "users/")
                + "[0-9a-f-]{36}/[0-9a-f-]{36}\\.(png|jpg)", value);
    }

    public void deleteOwned(UUID accountId, String key) {
        if (!isStoredKey(key) || !key.startsWith(prefix() + "users/" + accountId + "/")) return;
        deleteQuietly(key);
    }

    public void deleteStaging(UUID accountId, String key) {
        finalKey(accountId, key);
        deleteQuietly(key);
    }

    private void deleteQuietly(String key) {
        if (!properties.enabled()) return;
        try {
            clients.getObject().deleteObject(builder -> builder.bucket(properties.bucket()).key(key));
        } catch (SdkException exception) {
            log.warn("Could not clean up avatar object {}", key);
        }
    }

    private void validateImage(byte[] bytes, String contentType) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ApiException(ErrorCode.AUTH_AVATAR_INVALID);
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String expected = "image/png".equals(contentType) ? "PNG" : "JPEG";
                if (!expected.equalsIgnoreCase(reader.getFormatName()) || reader.getWidth(0) <= 0
                        || reader.getHeight(0) <= 0 || reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048) {
                    throw new ApiException(ErrorCode.AUTH_AVATAR_INVALID);
                }
                reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.AUTH_AVATAR_INVALID);
        }
    }

    private String prefix() {
        return properties.avatarPrefix().replaceAll("^/+|/+$", "") + "/";
    }

    private void requireEnabled() {
        if (!properties.enabled()) throw new ApiException(ErrorCode.AUTH_AVATAR_STORAGE_UNAVAILABLE);
    }
}
