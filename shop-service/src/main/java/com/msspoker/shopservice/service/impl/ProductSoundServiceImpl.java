package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.dto.response.ProductSoundResponse;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.ProductSoundService;
import com.msspoker.shopservice.service.S3StorageService;
import com.msspoker.shopservice.util.MetadataJsonMerger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSoundServiceImpl implements ProductSoundService {

    public static final long MAX_SOUND_SIZE = 2 * 1024 * 1024; // 2MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "ogg", "wav");

    private final ProductRepository productRepository;
    private final S3StorageService s3StorageService;
    private final MetadataJsonMerger metadataJsonMerger;

    @Override
    @Transactional
    public ProductSoundResponse uploadSound(UUID productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ProductExceptions.invalidSoundFile("Tệp âm thanh tải lên không được để trống.");
        }

        if (file.getSize() > MAX_SOUND_SIZE) {
            throw ProductExceptions.invalidSoundFile("Dung lượng tệp âm thanh không được vượt quá 2MB.");
        }

        String originalName = file.getOriginalFilename();
        String extension = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw ProductExceptions.invalidSoundFile("Chỉ chấp nhận các định dạng âm thanh: mp3, ogg, wav.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read audio file bytes: {}", e.getMessage(), e);
            throw ProductExceptions.invalidSoundFile("Lỗi khi đọc dữ liệu tệp âm thanh.");
        }

        validateAudioMagicBytes(bytes, extension);

        Product product = productRepository.findById(productId)
                .orElseThrow(ProductExceptions::productNotFound);

        String oldSoundUrl = metadataJsonMerger.getStringField(product.getMetadata(), "soundUrl");

        String version = "v" + Instant.now().toEpochMilli();
        String objectKey = "poker/sounds/" + productId + "/" + version + "." + extension;
        String contentType = resolveAudioContentType(extension);

        String soundUrl = s3StorageService.uploadFile(objectKey, bytes, contentType);

        String updatedMetadata = metadataJsonMerger.setStringField(product.getMetadata(), "soundUrl", soundUrl);
        product.setMetadata(updatedMetadata);
        productRepository.save(product);

        log.info("Uploaded sound for product {} -> soundUrl: {}", productId, soundUrl);

        // Asynchronously clean up old sound file if present
        if (oldSoundUrl != null && !oldSoundUrl.isBlank()) {
            cleanUpOldSoundFile(oldSoundUrl, objectKey);
        }

        return ProductSoundResponse.builder()
                .productId(productId)
                .soundUrl(soundUrl)
                .message("Tải lên tệp âm thanh cho sản phẩm thành công.")
                .build();
    }

    @Override
    @Transactional
    public ProductSoundResponse deleteSound(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(ProductExceptions::productNotFound);

        String oldSoundUrl = metadataJsonMerger.getStringField(product.getMetadata(), "soundUrl");
        if (oldSoundUrl == null || oldSoundUrl.isBlank()) {
            return ProductSoundResponse.builder()
                    .productId(productId)
                    .soundUrl(null)
                    .message("Sản phẩm chưa có tệp âm thanh nào được gắn.")
                    .build();
        }

        String updatedMetadata = metadataJsonMerger.removeField(product.getMetadata(), "soundUrl");
        product.setMetadata(updatedMetadata);
        productRepository.save(product);

        log.info("Removed sound from product {}", productId);
        cleanUpOldSoundFile(oldSoundUrl, null);

        return ProductSoundResponse.builder()
                .productId(productId)
                .soundUrl(null)
                .message("Đã gỡ tệp âm thanh khỏi sản phẩm thành công.")
                .build();
    }

    private void cleanUpOldSoundFile(String oldUrl, String newKey) {
        CompletableFuture.runAsync(() -> {
            try {
                int soundIdx = oldUrl.indexOf("poker/sounds/");
                if (soundIdx >= 0) {
                    String oldKey = oldUrl.substring(soundIdx);
                    if (newKey == null || !oldKey.equals(newKey)) {
                        s3StorageService.deleteFolder(oldKey);
                        log.info("Deleted old sound object in S3: {}", oldKey);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to delete old sound from S3: {}", e.getMessage());
            }
        });
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }

    private void validateAudioMagicBytes(byte[] bytes, String extension) {
        if (bytes == null || bytes.length < 4) {
            throw ProductExceptions.invalidSoundFile("Nội dung tệp quá nhỏ, không phải âm thanh hợp lệ.");
        }

        switch (extension) {
            case "mp3" -> {
                // ID3 tag header: 0x49 0x44 0x33 ("ID3")
                boolean hasId3 = bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3';
                // MPEG audio sync frame: 11 bits set to 1 (0xFF, then high 3 bits of byte 1 set)
                boolean hasMpegSync = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0;
                if (!hasId3 && !hasMpegSync) {
                    throw ProductExceptions.invalidSoundFile("Nội dung tệp không phải định dạng MP3 hợp lệ.");
                }
            }
            case "ogg" -> {
                // OggS header: 0x4F 0x67 0x67 0x53
                boolean hasOggs = bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S';
                if (!hasOggs) {
                    throw ProductExceptions.invalidSoundFile("Nội dung tệp không phải định dạng OGG hợp lệ.");
                }
            }
            case "wav" -> {
                // RIFF....WAVE header
                if (bytes.length < 12) {
                    throw ProductExceptions.invalidSoundFile("Nội dung tệp WAV không đủ kích thước header.");
                }
                boolean hasRiff = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F';
                boolean hasWave = bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
                if (!hasRiff || !hasWave) {
                    throw ProductExceptions.invalidSoundFile("Nội dung tệp không phải định dạng WAV hợp lệ.");
                }
            }
        }
    }

    private String resolveAudioContentType(String extension) {
        return switch (extension) {
            case "mp3" -> "audio/mpeg";
            case "ogg" -> "audio/ogg";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
