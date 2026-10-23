package com.msspoker.shopservice.config;

import com.msspoker.shopservice.dto.response.CategoryResponse;
import com.msspoker.shopservice.dto.response.PageResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.enums.ProductTier;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RedisSerializationTest {

    @Test
    void testSerializationWithConfiguredSerializer() {
        RedisConfig config = new RedisConfig();
        RedisSerializer<Object> serializer = config.redisJsonSerializer();

        // 1. Test List<CategoryResponse>
        List<CategoryResponse> categories = new ArrayList<>(List.of(
                new CategoryResponse(UUID.randomUUID(), "CARD_SKIN", "Card Skin")
        ));
        byte[] bytesCat = serializer.serialize(categories);
        System.out.println("Serialized categories: " + new String(bytesCat));
        Object deserializedCat = serializer.deserialize(bytesCat);
        System.out.println("Deserialized categories: " + deserializedCat);
        assertNotNull(deserializedCat);

        // 2. Test PageResponse<ProductResponse>
        ProductResponse product = ProductResponse.builder()
                .id(UUID.randomUUID())
                .name("Test Product")
                .tier(ProductTier.VIP)
                .build();
        PageResponse<ProductResponse> page = new PageResponse<>(
                List.of(product), 0, 20, 1, 1, true
        );
        byte[] bytesPage = serializer.serialize(page);
        System.out.println("Serialized page: " + new String(bytesPage));
        Object deserializedPage = serializer.deserialize(bytesPage);
        System.out.println("Deserialized page: " + deserializedPage);
        assertNotNull(deserializedPage);
    }
}
