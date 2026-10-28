package com.msspoker.shopservice.datainit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.config.DataInitProperties;
import com.msspoker.shopservice.config.RedisConfig;
import com.msspoker.shopservice.constant.ProductAssetConstants;
import com.msspoker.shopservice.dto.response.CardDeckManifest;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.util.MetadataJsonMerger;
import com.msspoker.shopservice.validator.CardDeckValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Creates the seed products whose files live in the shared bucket when their id is missing, so a fresh
 * database (new machine, `docker compose down -v`) shows them at once. DemoAccountGrantJob then grants them
 * to the demo accounts. Existing products, including admin edits, are never touched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shop.data-init.enabled", havingValue = "true")
@EnableConfigurationProperties(DataInitProperties.class)
public class ShopDataInitializer implements ApplicationRunner {

    private final DataInitProperties properties;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MetadataJsonMerger metadataJsonMerger;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Override
    public void run(ApplicationArguments args) {
        boolean created = seed(SeedProducts.MOCK_DECK_ID, ProductAssetConstants.CARD_SKIN_CATEGORY, this::mockDeck);
        created |= seed(SeedProducts.TAUNT_SOUND_ID, ProductAssetConstants.SOUND_CATEGORY, this::tauntSound);
        for (SeedProducts.Dealer dealer : SeedProducts.DEALERS) {
            created |= seed(dealer.id(), ProductAssetConstants.DEALER_CATEGORY, category -> dealer(category, dealer));
        }
        if (created) {
            // The catalog may still be cached in Redis from before this database was reset.
            Optional.ofNullable(cacheManager.getCache(RedisConfig.CACHE_PRODUCTS_CATALOG)).ifPresent(Cache::clear);
        }
    }

    /** Returns true when the product was created. */
    private boolean seed(UUID id, String categoryCode, Function<Category, Product> definition) {
        if (productRepository.existsById(id)) {
            log.info("Seed product {} already exists; left unchanged", id);
            return false;
        }
        Optional<Category> category = categoryRepository.findByCode(categoryCode);
        if (category.isEmpty()) {
            log.warn("Category {} is missing; seed product {} skipped", categoryCode, id);
            return false;
        }
        Product product = productRepository.save(definition.apply(category.get()));
        log.info("Created seed product {} ({})", product.getName(), categoryCode);
        return true;
    }

    private Product mockDeck(Category category) {
        Map<String, String> cards = new TreeMap<>();
        CardDeckValidator.CORE_CARD_CODES.forEach(code ->
                cards.put(code, assetUrl(SeedProducts.MOCK_DECK_FOLDER + code + SeedProducts.CARD_IMAGE_EXTENSION)));
        String backUrl = cards.get(CardDeckValidator.CARD_BACK_CODE);
        String previewUrl = cards.get(SeedProducts.MOCK_DECK_PREVIEW_CODE);
        CardDeckManifest manifest = CardDeckManifest.builder()
                .skinType(SeedProducts.DECK_SKIN_TYPE)
                .version(SeedProducts.DECK_VERSION)
                .cardBackUrl(backUrl)
                .previewUrl(previewUrl)
                .cards(cards)
                .build();
        return Product.builder()
                .id(SeedProducts.MOCK_DECK_ID)
                .category(category)
                .name("Bộ Bài Thử Nghiệm")
                .description("Bộ bài 53 lá mẫu để kiểm thử skin bài trên bàn chơi.")
                .tier(ProductTier.VIP)
                .priceVnd(50000)
                .priceVipCoin(50)
                .assetUrl(backUrl)
                .previewUrl(previewUrl)
                .metadata(toJson(manifest))
                .build();
    }

    private Product tauntSound(Category category) {
        String iconUrl = assetUrl(SeedProducts.TAUNT_SOUND_ICON);
        String metadata = metadataJsonMerger.setStringField(null, ProductAssetConstants.SOUND_URL_KEY,
                assetUrl(SeedProducts.TAUNT_SOUND_FILE));
        return Product.builder()
                .id(SeedProducts.TAUNT_SOUND_ID)
                .category(category)
                .name("Chó Cao Bằng Bỏ PC")
                .description("Âm thanh trêu chọc cả bàn, bấm được bất cứ lúc nào trong trận.")
                .tier(ProductTier.NORMAL)
                .priceVnd(20000)
                .priceVipCoin(20)
                .assetUrl(iconUrl)
                .previewUrl(iconUrl)
                .metadata(metadata)
                .build();
    }

    /** Same metadata and asset URL as the admin dealer form: every pose under "poses", the avatar as the asset. */
    private Product dealer(Category category, SeedProducts.Dealer dealer) {
        Map<String, String> poses = new LinkedHashMap<>();
        ProductAssetConstants.DEALER_POSES.forEach(pose ->
                poses.put(pose, assetUrl(dealer.folder() + pose + SeedProducts.DEALER_IMAGE_EXTENSION)));
        String avatarUrl = poses.get(ProductAssetConstants.DEALER_AVATAR_POSE);
        return Product.builder()
                .id(dealer.id())
                .category(category)
                .name(dealer.name())
                .description(dealer.description())
                .tier(dealer.tier())
                .priceVnd(dealer.priceVnd())
                .priceVipCoin(dealer.priceVipCoin())
                .assetUrl(avatarUrl)
                .previewUrl(avatarUrl)
                .metadata(toJson(Map.of(ProductAssetConstants.DEALER_POSES_KEY, poses)))
                .build();
    }

    private String assetUrl(String key) {
        String base = properties.assetBaseUrl();
        return (base.endsWith("/") ? base : base + "/") + key;
    }

    private String toJson(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Seed product metadata is not serializable", e);
        }
    }
}
