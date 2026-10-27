package com.msspoker.shopservice.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.dto.response.AiModelDescriptor;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiAiModelProviderTest {

    private GeminiAiModelProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        provider = new GeminiAiModelProvider(objectMapper);
    }

    @Test
    @DisplayName("getProviderType: trả về GEMINI")
    void getProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(AiProviderType.GEMINI);
    }

    @Test
    @DisplayName("listAvailableModels: trả về danh sách model Gemini và Imagen")
    void listAvailableModels() {
        List<AiModelDescriptor> models = provider.listAvailableModels();
        assertThat(models).isNotEmpty();
        assertThat(models).extracting(AiModelDescriptor::modelName)
                .contains("gemini-1.5-pro", "gemini-1.5-flash", "imagen-3.0-generate-002");
    }

    @Test
    @DisplayName("generateStyleSpec: fallback trả về JSON spec hợp lệ khi không có API key")
    void generateStyleSpec_FallbackProducesValidJson() throws Exception {
        AiModelConfig config = AiModelConfig.builder()
                .modelName("gemini-1.5-pro")
                .provider(AiProviderType.GEMINI)
                .build();

        String spec = provider.generateStyleSpec(config, "Thiết kế bộ bài Cyberpunk dạ quang", null, null);

        assertThat(spec).isNotBlank();
        assertThat(objectMapper.readTree(spec).has("theme")).isTrue();
        assertThat(objectMapper.readTree(spec).has("palette")).isTrue();
    }

    @Test
    @DisplayName("generateDealerPoses: sinh đủ 4 tư thế (idle, deal, flip, win)")
    void generateDealerPoses_ReturnsAll4Poses() {
        AiModelConfig config = AiModelConfig.builder()
                .modelName("imagen-3.0-generate-002")
                .provider(AiProviderType.GEMINI)
                .build();

        Map<String, byte[]> poses = provider.generateDealerPoses(config, null, null, "Cô gái chia bài tóc vàng");

        assertThat(poses).containsKeys("idle", "deal", "flip", "win");
        assertThat(poses.get("idle")).isNotEmpty();
        assertThat(poses.get("deal")).isNotEmpty();
        assertThat(poses.get("flip")).isNotEmpty();
        assertThat(poses.get("win")).isNotEmpty();
    }
}
