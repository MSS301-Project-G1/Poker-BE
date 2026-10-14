package com.msspoker.shopservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.validator.CardDeckValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TextureAtlasGeneratorTest {

    private TextureAtlasGenerator atlasGenerator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        atlasGenerator = new TextureAtlasGenerator(objectMapper);
    }

    @Test
    @DisplayName("generateAtlas: Ghép thành công 53 lá bài thành Texture Atlas Sprite Sheet PNG và JSON frames")
    void generateAtlas_success() throws IOException {
        // Tạo ảnh mẫu nhỏ 20x30 cho 53 lá
        byte[] samplePng = createDummyCardPng(20, 30, Color.RED);

        Map<String, byte[]> cardImages = new LinkedHashMap<>();
        for (String code : CardDeckValidator.CORE_CARD_CODES) {
            cardImages.put(code, samplePng);
        }
        assertThat(cardImages).hasSize(53);

        TextureAtlasGenerator.AtlasResult result = atlasGenerator.generateAtlas(cardImages);

        assertThat(result).isNotNull();
        assertThat(result.imageBytes()).isNotEmpty();
        assertThat(result.jsonBytes()).isNotEmpty();

        // Kiểm tra JSON frames
        JsonNode root = objectMapper.readTree(result.jsonBytes());
        assertThat(root.has("frames")).isTrue();
        assertThat(root.has("meta")).isTrue();

        JsonNode frames = root.get("frames");
        assertThat(frames.size()).isEqualTo(53);
        assertThat(frames.has("BACK")).isTrue();
        assertThat(frames.has("2H")).isTrue();
        assertThat(frames.has("AS")).isTrue();

        JsonNode meta = root.get("meta");
        assertThat(meta.get("totalCards").asInt()).isEqualTo(53);
        assertThat(meta.get("format").asText()).isEqualTo("RGBA8888");
    }

    private byte[] createDummyCardPng(int width, int height, Color color) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
