package com.msspoker.shopservice.datainit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.config.DataInitProperties;
import com.msspoker.shopservice.config.RedisConfig;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.util.MetadataJsonMerger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopDataInitializerTest {

    private static final String BASE_URL = "https://bucket.test/poker/shop/seed";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache catalogCache;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ShopDataInitializer initializer() {
        return new ShopDataInitializer(new DataInitProperties(BASE_URL, "http://auth.test", List.of()), productRepository,
                categoryRepository, new MetadataJsonMerger(objectMapper), objectMapper, cacheManager);
    }

    private static Category category(String code) {
        return Category.builder().id(UUID.randomUUID()).code(code).name(code).build();
    }

    @Test
    @DisplayName("Database mới: tạo bộ bài, âm thanh và 2 dealer seed với URL từ bucket chung, xóa cache catalog")
    void seedsMissingProducts() throws Exception {
        when(productRepository.existsById(any())).thenReturn(false);
        when(categoryRepository.findByCode("CARD_SKIN")).thenReturn(Optional.of(category("CARD_SKIN")));
        when(categoryRepository.findByCode("SOUND")).thenReturn(Optional.of(category("SOUND")));
        when(categoryRepository.findByCode("DEALER")).thenReturn(Optional.of(category("DEALER")));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache(RedisConfig.CACHE_PRODUCTS_CATALOG)).thenReturn(catalogCache);

        initializer().run(null);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(4)).save(saved.capture());
        Product deck = saved.getAllValues().get(0);
        Product sound = saved.getAllValues().get(1);

        assertThat(deck.getId()).isEqualTo(SeedProducts.MOCK_DECK_ID);
        assertThat(deck.getAssetUrl()).isEqualTo(BASE_URL + "/cards/mock-deck/BACK.png");
        JsonNode manifest = objectMapper.readTree(deck.getMetadata());
        assertThat(manifest.get("skinType").asText()).isEqualTo("CARD_DECK_53");
        assertThat(manifest.get("cards").size()).isEqualTo(53);
        assertThat(manifest.get("cards").get("10H").asText()).isEqualTo(BASE_URL + "/cards/mock-deck/10H.png");
        assertThat(manifest.get("cardBackUrl").asText()).isEqualTo(deck.getAssetUrl());

        assertThat(sound.getId()).isEqualTo(SeedProducts.TAUNT_SOUND_ID);
        assertThat(sound.getAssetUrl()).isEqualTo(BASE_URL + "/sounds/cho-cao-bang/icon.webp");
        assertThat(objectMapper.readTree(sound.getMetadata()).get("soundUrl").asText())
                .isEqualTo(BASE_URL + "/sounds/cho-cao-bang/sound.mp3");

        Product ruby = saved.getAllValues().get(2);
        assertThat(ruby.getId()).isEqualTo(SeedProducts.DEALERS.get(0).id());
        assertThat(ruby.getAssetUrl()).isEqualTo(BASE_URL + "/dealers/ruby/avatar.png");
        JsonNode poses = objectMapper.readTree(ruby.getMetadata()).get("poses");
        assertThat(poses.size()).isEqualTo(5);
        assertThat(poses.get("idle").asText()).isEqualTo(BASE_URL + "/dealers/ruby/idle.png");
        assertThat(poses.get("win").asText()).isEqualTo(BASE_URL + "/dealers/ruby/win.png");
        assertThat(saved.getAllValues().get(3).getAssetUrl()).isEqualTo(BASE_URL + "/dealers/neon/avatar.png");

        verify(catalogCache).clear();
    }

    @Test
    @DisplayName("Sản phẩm seed đã tồn tại: không ghi đè, không xóa cache")
    void leavesExistingProductsUnchanged() {
        when(productRepository.existsById(any())).thenReturn(true);

        initializer().run(null);

        verify(productRepository, never()).save(any());
        verify(cacheManager, never()).getCache(any());
    }

    @Test
    @DisplayName("Thiếu danh mục: bỏ qua sản phẩm seed thay vì làm hỏng khởi động")
    void skipsProductWhenCategoryMissing() {
        when(productRepository.existsById(any())).thenReturn(false);
        when(categoryRepository.findByCode(any())).thenReturn(Optional.empty());

        initializer().run(null);

        verify(productRepository, never()).save(any());
    }
}
