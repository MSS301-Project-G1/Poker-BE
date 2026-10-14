package com.msspoker.shopservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.dto.response.CardDeckManifest;
import com.msspoker.shopservice.dto.response.CardSkinUploadResponse;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.CardSkinExceptions;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.CardDeckSkinService;
import com.msspoker.shopservice.service.S3StorageService;
import com.msspoker.shopservice.service.TextureAtlasGenerator;
import com.msspoker.shopservice.validator.CardDeckValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardDeckSkinServiceImpl implements CardDeckSkinService {

    private final ProductRepository productRepository;
    private final S3StorageService s3StorageService;
    private final TextureAtlasGenerator textureAtlasGenerator;
    private final CardDeckValidator cardDeckValidator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CardSkinUploadResponse uploadDeckSkinZip(UUID productId, MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw CardSkinExceptions.invalidZipArchive("File ZIP tải lên không được để trống.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(CardSkinExceptions::productNotFound);

        // 1. Trích xuất các file ảnh từ file ZIP vào bộ nhớ
        Map<String, byte[]> cardImages = extractImagesFromZip(zipFile);

        // 2. Thực hiện quy trình xử lý và cập nhật vào S3 + DB
        return processAndSaveCardSkin(product, cardImages);
    }

    @Override
    @Transactional
    public CardSkinUploadResponse uploadDeckSkinFiles(UUID productId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw CardSkinExceptions.invalidZipArchive("Danh sách file ảnh tải lên không được để trống.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(CardSkinExceptions::productNotFound);

        Map<String, byte[]> cardImages = new LinkedHashMap<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String code = cardDeckValidator.extractCardCode(file.getOriginalFilename());
                if (code != null) {
                    try {
                        cardImages.put(code, file.getBytes());
                    } catch (IOException e) {
                        throw CardSkinExceptions.invalidZipArchive("Lỗi khi đọc file " + file.getOriginalFilename());
                    }
                }
            }
        }

        return processAndSaveCardSkin(product, cardImages);
    }

    private Map<String, byte[]> extractImagesFromZip(MultipartFile zipFile) {
        Map<String, byte[]> cardImages = new LinkedHashMap<>();

        try (InputStream is = zipFile.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                cardDeckValidator.validateZipEntry(entryName);

                String cardCode = cardDeckValidator.extractCardCode(entryName);
                if (cardCode == null) {
                    // Bỏ qua các file không phải ảnh bài (ví dụ .DS_Store, Thumbs.db, text)
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                byte[] bytes = baos.toByteArray();
                if (bytes.length > 0) {
                    cardImages.put(cardCode, bytes);
                }
            }

        } catch (IOException e) {
            log.error("Failed to read zip archive: {}", e.getMessage(), e);
            throw CardSkinExceptions.invalidZipArchive(e.getMessage());
        }

        return cardImages;
    }

    private CardSkinUploadResponse processAndSaveCardSkin(Product product, Map<String, byte[]> cardImages) {
        // 1. Kiểm tra tính đầy đủ của 53 lá cốt lõi (52 lá front + 1 lá back)
        cardDeckValidator.validateCoreCards(cardImages.keySet());

        // 2. Tạo version định danh theo timestamp (Cache busting)
        String version = "v" + Instant.now().toEpochMilli();
        String basePath = "skins/cards/" + product.getId() + "/";
        String currentVersionPath = basePath + version + "/";

        // Ghi nhận version cũ để dọn dẹp sau khi lưu thành công
        String oldVersionPath = extractOldVersionPath(product.getMetadata());

        // 3. Tự động ghép Texture Atlas Sprite Sheet (PNG) + coordinates JSON
        TextureAtlasGenerator.AtlasResult atlasResult = textureAtlasGenerator.generateAtlas(cardImages);

        // 4. Chuẩn bị danh sách files để tải lên song song lên S3
        Map<String, byte[]> uploadBatch = new HashMap<>();

        // Từng lá bài riêng lẻ
        for (Map.Entry<String, byte[]> entry : cardImages.entrySet()) {
            String cardCode = entry.getKey();
            uploadBatch.put(currentVersionPath + cardCode + ".png", entry.getValue());
        }

        // Texture Atlas & Frames JSON
        String atlasImgKey = currentVersionPath + "deck_atlas.png";
        String atlasJsonKey = currentVersionPath + "deck_atlas.json";
        uploadBatch.put(atlasImgKey, atlasResult.imageBytes());
        uploadBatch.put(atlasJsonKey, atlasResult.jsonBytes());

        // 5. Tải lên S3 song song qua CompletableFuture
        Map<String, String> uploadedUrls = s3StorageService.uploadBatch(uploadBatch, "image/png");

        // 6. Xây dựng CardDeckManifest
        String cardBackUrl = uploadedUrls.get(currentVersionPath + CardDeckValidator.CARD_BACK_CODE + ".png");
        String previewUrl = uploadedUrls.getOrDefault(currentVersionPath + "PREVIEW.png", cardBackUrl);
        String atlasTextureUrl = uploadedUrls.get(atlasImgKey);
        String atlasCoordinatesUrl = uploadedUrls.get(atlasJsonKey);

        Map<String, String> cardUrlMap = new TreeMap<>();
        for (String cardCode : cardImages.keySet()) {
            String url = uploadedUrls.get(currentVersionPath + cardCode + ".png");
            if (url != null) {
                cardUrlMap.put(cardCode, url);
            }
        }

        CardDeckManifest manifest = CardDeckManifest.builder()
                .skinType("CARD_DECK_53")
                .version(version)
                .cardBackUrl(cardBackUrl)
                .previewUrl(previewUrl)
                .atlas(CardDeckManifest.AtlasInfo.builder()
                        .textureUrl(atlasTextureUrl)
                        .coordinatesUrl(atlasCoordinatesUrl)
                        .build())
                .cards(cardUrlMap)
                .build();

        // 7. Cập nhật vào PostgreSQL (chỉ lưu Metadata JSONB, không lưu binary)
        try {
            String manifestJson = objectMapper.writeValueAsString(manifest);
            product.setAssetUrl(atlasTextureUrl);
            product.setPreviewUrl(previewUrl);
            product.setMetadata(manifestJson);
            productRepository.save(product);
            log.info("Saved card skin product {} metadata to PostgreSQL ({} cards)", product.getId(), cardUrlMap.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize card skin manifest: {}", e.getMessage(), e);
            throw CardSkinExceptions.textureAtlasGenerationError("Lỗi chuyển đổi dữ liệu cấu hình bộ bài sang JSON.");
        }

        // 8. Dọn dẹp phiên bản cũ trên S3 trong background (nếu có)
        if (oldVersionPath != null && !oldVersionPath.equals(currentVersionPath)) {
            CompletableFuture.runAsync(() -> s3StorageService.deleteFolder(oldVersionPath));
        }

        return CardSkinUploadResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .totalCardsUploaded(cardImages.size())
                .version(version)
                .previewUrl(previewUrl)
                .textureAtlasUrl(atlasTextureUrl)
                .coordinatesUrl(atlasCoordinatesUrl)
                .manifest(manifest)
                .build();
    }

    private String extractOldVersionPath(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            if (root.has("version") && root.has("card_back_url")) {
                String cardBack = root.get("card_back_url").asText("");
                int cardsIdx = cardBack.indexOf("skins/cards/");
                if (cardsIdx >= 0) {
                    String sub = cardBack.substring(cardsIdx);
                    // Lấy đến hết /v{timestamp}/
                    int vIdx = sub.indexOf("/v");
                    if (vIdx >= 0) {
                        int nextSlash = sub.indexOf('/', vIdx + 1);
                        if (nextSlash >= 0) {
                            return sub.substring(0, nextSlash + 1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
