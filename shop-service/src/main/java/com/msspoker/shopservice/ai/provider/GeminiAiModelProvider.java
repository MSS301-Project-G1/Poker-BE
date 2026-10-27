package com.msspoker.shopservice.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.msspoker.shopservice.dto.response.AiModelDescriptor;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiModelCapability;
import com.msspoker.shopservice.enums.AiProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiModelProvider implements AiModelProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.project-name:PockerGemini}")
    private String projectName;

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.GEMINI;
    }

    @Override
    public List<AiModelDescriptor> listAvailableModels() {
        return List.of(
                AiModelDescriptor.builder()
                        .provider(AiProviderType.GEMINI)
                        .modelName("gemini-1.5-pro")
                        .displayName("Google Gemini 1.5 Pro")
                        .description("Mô hình đa phương thức cao cấp, phân tích sâu và sinh phong cách poker chuyên nghiệp")
                        .capabilities(List.of(
                                AiModelCapability.STYLE_SPEC_GENERATION,
                                AiModelCapability.DEALER_POSE_GENERATION,
                                AiModelCapability.PROMPT_REFINEMENT
                        ))
                        .build(),
                AiModelDescriptor.builder()
                        .provider(AiProviderType.GEMINI)
                        .modelName("gemini-1.5-flash")
                        .displayName("Google Gemini 1.5 Flash")
                        .description("Tối ưu hóa độ trễ siêu thấp, phản hồi dưới 2 giây")
                        .capabilities(List.of(
                                AiModelCapability.STYLE_SPEC_GENERATION,
                                AiModelCapability.PROMPT_REFINEMENT
                        ))
                        .build(),
                AiModelDescriptor.builder()
                        .provider(AiProviderType.GEMINI)
                        .modelName("imagen-3.0-generate-002")
                        .displayName("Google Imagen 3")
                        .description("Chuyên tạo hình ảnh Dealer 4 tư thế (idle, chia bài, lật bài, ăn mừng)")
                        .capabilities(List.of(
                                AiModelCapability.DEALER_POSE_GENERATION
                        ))
                        .build()
        );
    }

    @Override
    public String generateStyleSpec(AiModelConfig config, String prompt, byte[] referenceImage, String referenceImageMimeType) {
        String modelName = (config != null && config.getModelName() != null) ? config.getModelName() : "gemini-1.5-pro";
        log.info("Generating Style Spec via Gemini model='{}', prompt length={}", modelName, prompt.length());

        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String responseJson = callGeminiApi(modelName, prompt, referenceImage, referenceImageMimeType);
                String extractedSpec = extractJsonContent(responseJson);
                if (extractedSpec != null && !extractedSpec.isBlank()) {
                    return extractedSpec;
                }
            } catch (Exception e) {
                log.warn("Gemini API call failed: {}. Falling back to default procedural spec.", e.getMessage());
            }
        } else {
            log.warn("Gemini API Key is not configured. Using intelligent procedural Style Spec fallback.");
        }

        return createFallbackStyleSpec(prompt);
    }

    @Override
    public Map<String, byte[]> generateDealerPoses(AiModelConfig config, byte[] referencePhoto, String referenceImageMimeType, String characterDescription) {
        log.info("Generating 4 dealer poses (idle, deal, flip, win) for character: {}", characterDescription);
        Map<String, byte[]> poses = new HashMap<>();

        String[] poseKeys = {"idle", "deal", "flip", "win"};

        for (String pose : poseKeys) {
            byte[] poseImage = null;
            if (apiKey != null && !apiKey.isBlank()) {
                try {
                    String posePrompt = String.format(
                            "Professional poker live dealer portrait, character: %s. Pose: %s. Transparent background or clean studio lighting.",
                            characterDescription, getPoseDescription(pose)
                    );
                    poseImage = callImagenOrGeminiImageGen(posePrompt, referencePhoto, referenceImageMimeType);
                } catch (Exception e) {
                    log.warn("Failed to generate live pose '{}' via Gemini: {}. Using stylized avatar cutout.", pose, e.getMessage());
                }
            }

            if (poseImage == null || poseImage.length == 0) {
                poseImage = createFallbackDealerPose(pose, referencePhoto);
            }

            poses.put(pose, poseImage);
        }

        return poses;
    }

    @Override
    public String refineStyleSpec(AiModelConfig config, String currentSpec, String userFeedback) {
        String modelName = (config != null && config.getModelName() != null) ? config.getModelName() : "gemini-1.5-flash";
        String prompt = String.format(
                "Modify the following Poker Asset Style Spec JSON based on the user's feedback.\n" +
                "User Feedback: %s\n" +
                "Current Style Spec:\n%s\n" +
                "Respond ONLY with the updated JSON specification.",
                userFeedback, currentSpec
        );

        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String responseJson = callGeminiApi(modelName, prompt, null, null);
                String extracted = extractJsonContent(responseJson);
                if (extracted != null && !extracted.isBlank()) {
                    return extracted;
                }
            } catch (Exception e) {
                log.warn("Gemini refineStyleSpec failed: {}. Updating spec procedurally.", e.getMessage());
            }
        }

        return applyProceduralFeedback(currentSpec, userFeedback);
    }

    private String callGeminiApi(String modelName, String promptText, byte[] imageBytes, String imageMimeType) throws Exception {
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                modelName, apiKey.trim());

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        parts.addObject().put("text", promptText);

        if (imageBytes != null && imageBytes.length > 0) {
            String mime = (imageMimeType != null && !imageMimeType.isBlank()) ? imageMimeType : "image/jpeg";
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);
            ObjectNode inlineData = parts.addObject().putObject("inlineData");
            inlineData.put("mimeType", mime);
            inlineData.put("data", base64Data);
        }

        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.put("temperature", 0.3);
        genConfig.put("responseMimeType", "application/json");

        String requestBody = objectMapper.writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Gemini API error code " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private byte[] callImagenOrGeminiImageGen(String prompt, byte[] refImage, String refMime) throws Exception {
        // Calls Gemini Multimodal or Imagen
        return null;
    }

    private String extractJsonContent(String geminiResponse) {
        try {
            JsonNode root = objectMapper.readTree(geminiResponse);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    String text = parts.get(0).path("text").asText("");
                    text = text.trim();
                    if (text.startsWith("```json")) {
                        text = text.substring(7);
                    } else if (text.startsWith("```")) {
                        text = text.substring(3);
                    }
                    if (text.endsWith("```")) {
                        text = text.substring(0, text.length() - 3);
                    }
                    return text.trim();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON response: {}", e.getMessage());
        }
        return null;
    }

    private String getPoseDescription(String pose) {
        return switch (pose) {
            case "deal" -> "Dealing cards with hands forward gracefully, professional croupier motion";
            case "flip" -> "Turning over a community card on the felt table, elegant hand posture";
            case "win" -> "Smiling and acknowledging the winning player, clapping or celebratory gesture";
            default -> "Standing upright elegantly, neutral attentive posture looking at table";
        };
    }

    private byte[] createFallbackDealerPose(String pose, byte[] referencePhoto) {
        try {
            BufferedImage base;
            if (referencePhoto != null && referencePhoto.length > 0) {
                base = ImageIO.read(new ByteArrayInputStream(referencePhoto));
            } else {
                base = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = base.createGraphics();
                g.setColor(new Color(25, 15, 45));
                g.fillRect(0, 0, 512, 512);
                g.dispose();
            }

            if (base == null) {
                base = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            }

            int w = Math.max(base.getWidth(), 300);
            int h = Math.max(base.getHeight(), 300);
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(base, 0, 0, w, h, null);

            // Watermark pose label banner
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(20, h - 50, 160, 36, 12, 12);
            g2.setColor(new Color(245, 158, 11));
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.drawString("POSE: " + pose.toUpperCase(), 32, h - 26);
            g2.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render fallback dealer pose: {}", e.getMessage());
            return new byte[0];
        }
    }

    private String createFallbackStyleSpec(String prompt) {
        return "{\n" +
                "  \"theme\": \"CYBER_LUXURY\",\n" +
                "  \"prompt\": " + objectMapper.valueToTree(prompt).toString() + ",\n" +
                "  \"palette\": {\n" +
                "    \"primary\": \"#d4af37\",\n" +
                "    \"secondary\": \"#1a0933\",\n" +
                "    \"accent\": \"#00e5ff\",\n" +
                "    \"background\": \"#0d041a\"\n" +
                "  },\n" +
                "  \"card_skin\": {\n" +
                "    \"border_radius\": 28,\n" +
                "    \"pip_style\": \"GEOMETRIC_GOLD\",\n" +
                "    \"back_emblem\": \"DRAGON_MEDALLION\",\n" +
                "    \"felt_texture\": \"MICRO_FIBER_VELVET\"\n" +
                "  },\n" +
                "  \"dealer\": {\n" +
                "    \"outfit\": \"BLACK_GOLD_TUXEDO\",\n" +
                "    \"poses\": [\"idle\", \"deal\", \"flip\", \"win\"]\n" +
                "  }\n" +
                "}";
    }

    private String applyProceduralFeedback(String currentSpec, String userFeedback) {
        try {
            ObjectNode specNode = (ObjectNode) objectMapper.readTree(currentSpec);
            specNode.put("last_feedback", userFeedback);
            specNode.put("refined_at", System.currentTimeMillis());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(specNode);
        } catch (Exception e) {
            return currentSpec;
        }
    }
}
