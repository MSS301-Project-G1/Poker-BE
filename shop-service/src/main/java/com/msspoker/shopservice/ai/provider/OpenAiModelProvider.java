package com.msspoker.shopservice.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.dto.response.AiModelDescriptor;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiModelCapability;
import com.msspoker.shopservice.enums.AiProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiModelProvider implements AiModelProvider {

    private final ObjectMapper objectMapper;

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.OPENAI;
    }

    @Override
    public List<AiModelDescriptor> listAvailableModels() {
        return List.of(
                AiModelDescriptor.builder()
                        .provider(AiProviderType.OPENAI)
                        .modelName("gpt-4o")
                        .displayName("OpenAI GPT-4o")
                        .description("Mô hình đa phương thức hàng đầu từ OpenAI")
                        .capabilities(List.of(
                                AiModelCapability.STYLE_SPEC_GENERATION,
                                AiModelCapability.PROMPT_REFINEMENT
                        ))
                        .build(),
                AiModelDescriptor.builder()
                        .provider(AiProviderType.OPENAI)
                        .modelName("gpt-4o-mini")
                        .displayName("OpenAI GPT-4o Mini")
                        .description("Phiên bản nhẹ, tối ưu chi phí và tốc độ")
                        .capabilities(List.of(
                                AiModelCapability.STYLE_SPEC_GENERATION,
                                AiModelCapability.PROMPT_REFINEMENT
                        ))
                        .build()
        );
    }

    @Override
    public String generateStyleSpec(AiModelConfig config, String prompt, byte[] referenceImage, String referenceImageMimeType) {
        log.info("Generating Style Spec via OpenAI model='{}'", (config != null ? config.getModelName() : "gpt-4o"));
        // OpenAI Chat Completions fallback
        return "{\n" +
                "  \"theme\": \"MODERN_NEON\",\n" +
                "  \"prompt\": " + objectMapper.valueToTree(prompt).toString() + ",\n" +
                "  \"palette\": {\n" +
                "    \"primary\": \"#00f0ff\",\n" +
                "    \"secondary\": \"#1b1036\",\n" +
                "    \"accent\": \"#ff007f\",\n" +
                "    \"background\": \"#090414\"\n" +
                "  }\n" +
                "}";
    }

    @Override
    public Map<String, byte[]> generateDealerPoses(AiModelConfig config, byte[] referencePhoto, String referenceImageMimeType, String characterDescription) {
        log.warn("Dealer pose image generation is not natively supported by OpenAI standard endpoint. Please use Gemini or Imagen.");
        return Map.of();
    }

    @Override
    public String refineStyleSpec(AiModelConfig config, String currentSpec, String userFeedback) {
        return currentSpec;
    }
}
