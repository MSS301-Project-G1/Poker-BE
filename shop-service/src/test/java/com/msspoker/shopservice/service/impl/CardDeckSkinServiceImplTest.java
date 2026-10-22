package com.msspoker.shopservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.dto.response.CardSkinUploadResponse;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.engine.TextureAtlasGenerator;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.S3StorageService;
import com.msspoker.shopservice.validator.CardDeckValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardDeckSkinServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private S3StorageService s3StorageService;

    private TextureAtlasGenerator textureAtlasGenerator;
    private CardDeckValidator cardDeckValidator;
    private ObjectMapper objectMapper;

    private CardDeckSkinServiceImpl cardDeckSkinService;

    private UUID productId;
    private Product mockProduct;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        textureAtlasGenerator = new TextureAtlasGenerator(objectMapper);
        cardDeckValidator = new com.msspoker.shopservice.validator.impl.CardDeckValidatorImpl();

        cardDeckSkinService = new CardDeckSkinServiceImpl(
                productRepository,
                s3StorageService,
                textureAtlasGenerator,
                cardDeckValidator,
                objectMapper
        );

        productId = UUID.randomUUID();
        mockProduct = Product.builder()
                .id(productId)
                .name("Bộ Bài Cyberpunk VIP")
                .tier(ProductTier.VIP)
                .priceVnd(50000)
                .priceVipCoin(50)
                .assetUrl("https://example.com/old_asset.png")
                .build();
    }

    @Test
    @DisplayName("uploadDeckSkinZip: Tải lên và giải nén thành công file ZIP đủ 53 lá, nạp S3 và cập nhật DB")
    void uploadDeckSkinZip_success() throws IOException {
        // Tạo file ZIP in-memory chứa 53 lá
        byte[] zipBytes = createMockDeckZip(53);
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "deck.zip", "application/zip", zipBytes
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));

        // Mock uploadBatch trả về map URL
        when(s3StorageService.uploadBatch(anyMap(), anyString())).thenAnswer(invocation -> {
            Map<String, byte[]> batch = invocation.getArgument(0);
            Map<String, String> urls = new HashMap<>();
            for (String key : batch.keySet()) {
                urls.put(key, "http://localhost:9000/poker-assets/" + key);
            }
            return urls;
        });

        CardSkinUploadResponse response = cardDeckSkinService.uploadDeckSkinZip(productId, zipFile);

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.totalCardsUploaded()).isEqualTo(53);
        assertThat(response.textureAtlasUrl()).contains("deck_atlas.png");
        assertThat(response.coordinatesUrl()).contains("deck_atlas.json");
        assertThat(response.manifest()).isNotNull();
        assertThat(response.manifest().cards()).hasSize(53);

        verify(productRepository).save(any(Product.class));
        assertThat(mockProduct.getMetadata()).contains("CARD_DECK_53");
    }

    @Test
    @DisplayName("uploadDeckSkinZip: Ném ngoại lệ khi không tìm thấy Product")
    void uploadDeckSkinZip_productNotFound() throws IOException {
        byte[] zipBytes = createMockDeckZip(53);
        MockMultipartFile zipFile = new MockMultipartFile("file", "deck.zip", "application/zip", zipBytes);

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardDeckSkinService.uploadDeckSkinZip(productId, zipFile))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không tìm thấy sản phẩm");
    }

    @Test
    @DisplayName("uploadDeckSkinZip: Ném ngoại lệ khi file ZIP trống")
    void uploadDeckSkinZip_emptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]);

        assertThatThrownBy(() -> cardDeckSkinService.uploadDeckSkinZip(productId, emptyFile))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không được để trống");
    }

    @Test
    @DisplayName("uploadDeckSkinZip: Ném ngoại lệ khi ZIP thiếu lá bài cốt lõi")
    void uploadDeckSkinZip_missingCoreCards() throws IOException {
        // Tạo zip chỉ có 5 lá
        byte[] zipBytes = createMockDeckZip(5);
        MockMultipartFile zipFile = new MockMultipartFile("file", "partial.zip", "application/zip", zipBytes);

        when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));

        assertThatThrownBy(() -> cardDeckSkinService.uploadDeckSkinZip(productId, zipFile))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Bộ bài chưa đủ 53 lá cốt lõi");
    }

    private byte[] createMockDeckZip(int count) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            byte[] imgBytes = createDummyCardPng();

            int added = 0;
            for (String code : CardDeckValidator.CORE_CARD_CODES) {
                if (added >= count) break;
                ZipEntry entry = new ZipEntry(code + ".png");
                zos.putNextEntry(entry);
                zos.write(imgBytes);
                zos.closeEntry();
                added++;
            }
        }
        return baos.toByteArray();
    }

    private byte[] createDummyCardPng() throws IOException {
        BufferedImage img = new BufferedImage(15, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 15, 20);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
