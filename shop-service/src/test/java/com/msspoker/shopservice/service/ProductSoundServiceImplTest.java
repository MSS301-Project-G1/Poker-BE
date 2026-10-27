package com.msspoker.shopservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.dto.response.ProductSoundResponse;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.impl.ProductSoundServiceImpl;
import com.msspoker.shopservice.util.MetadataJsonMerger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSoundServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private S3StorageService s3StorageService;

    private MetadataJsonMerger metadataJsonMerger;
    private ProductSoundServiceImpl soundService;

    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        metadataJsonMerger = new MetadataJsonMerger(new ObjectMapper());
        soundService = new ProductSoundServiceImpl(productRepository, s3StorageService, metadataJsonMerger);

        productId = UUID.randomUUID();
        product = Product.builder()
                .id(productId)
                .name("Test Product")
                .tier(ProductTier.VIP)
                .priceVnd(50000L)
                .priceVipCoin(50L)
                .assetUrl("http://s3/test.png")
                .metadata("{\"cardBackUrl\":\"http://s3/back.png\"}")
                .build();
    }

    @Test
    @DisplayName("Từ chối tệp âm thanh vượt quá 2MB")
    void testUploadSoundFileTooLarge() {
        byte[] largeBytes = new byte[(int) (ProductSoundServiceImpl.MAX_SOUND_SIZE + 1024)];
        MockMultipartFile file = new MockMultipartFile("file", "sound.mp3", "audio/mpeg", largeBytes);

        assertThatThrownBy(() -> soundService.uploadSound(productId, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("vượt quá 2MB");
    }

    @Test
    @DisplayName("Từ chối tệp không thuộc định dạng mp3/ogg/wav")
    void testUploadSoundInvalidExtension() {
        byte[] bytes = new byte[]{1, 2, 3, 4, 5};
        MockMultipartFile file = new MockMultipartFile("file", "danger.exe", "application/octet-stream", bytes);

        assertThatThrownBy(() -> soundService.uploadSound(productId, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Chỉ chấp nhận các định dạng âm thanh");
    }

    @Test
    @DisplayName("Từ chối tệp giả mạo mp3 có magic bytes sai")
    void testUploadSoundFakeMp3MagicBytes() {
        byte[] fakeBytes = "NOT AN MP3 FILE TEXT".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.mp3", "audio/mpeg", fakeBytes);

        assertThatThrownBy(() -> soundService.uploadSound(productId, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không phải định dạng MP3 hợp lệ");
    }

    @Test
    @DisplayName("Tải lên tệp MP3 hợp lệ với ID3 tag thành công")
    void testUploadValidMp3WithId3Tag() {
        byte[] validMp3 = new byte[]{'I', 'D', '3', 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "click.mp3", "audio/mpeg", validMp3);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(s3StorageService.uploadFile(anyString(), any(byte[].class), eq("audio/mpeg")))
                .thenReturn("http://s3/poker/sounds/" + productId + "/v123.mp3");

        ProductSoundResponse response = soundService.uploadSound(productId, file);

        assertThat(response.soundUrl()).isEqualTo("http://s3/poker/sounds/" + productId + "/v123.mp3");
        verify(productRepository).save(product);
        assertThat(metadataJsonMerger.getStringField(product.getMetadata(), "soundUrl"))
                .isEqualTo("http://s3/poker/sounds/" + productId + "/v123.mp3");
        // Kiểm tra metadata cũ không bị mất
        assertThat(metadataJsonMerger.getStringField(product.getMetadata(), "cardBackUrl"))
                .isEqualTo("http://s3/back.png");
    }

    @Test
    @DisplayName("Gỡ tệp âm thanh khỏi sản phẩm thành công")
    void testDeleteSound() {
        product.setMetadata("{\"soundUrl\":\"http://s3/poker/sounds/" + productId + "/v100.mp3\",\"cardBackUrl\":\"http://s3/back.png\"}");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductSoundResponse response = soundService.deleteSound(productId);

        assertThat(response.soundUrl()).isNull();
        verify(productRepository).save(product);
        assertThat(metadataJsonMerger.getStringField(product.getMetadata(), "soundUrl")).isNull();
        assertThat(metadataJsonMerger.getStringField(product.getMetadata(), "cardBackUrl")).isEqualTo("http://s3/back.png");
    }
}
