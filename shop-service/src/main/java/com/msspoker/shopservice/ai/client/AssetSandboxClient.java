package com.msspoker.shopservice.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSandboxClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${asset-sandbox.url:http://localhost:8095}")
    private String sandboxUrl;

    @Builder
    public record RenderResult(
            boolean success,
            byte[] zipBytes,
            byte[] previewBytes,
            String manifestJson,
            int fileCount,
            Map<String, byte[]> posesPreviewBytes
    ) {}

    public RenderResult render(String category, String styleSpecJson, byte[] refImageBytes, Map<String, byte[]> posesMap) {
        log.info("Requesting asset rendering from Python sandbox category='{}' at {}", category, sandboxUrl);

        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("category", category);
            if (styleSpecJson != null && !styleSpecJson.isBlank()) {
                root.set("style_spec", objectMapper.readTree(styleSpecJson));
            }

            if (refImageBytes != null && refImageBytes.length > 0) {
                root.put("reference_image_base64", Base64.getEncoder().encodeToString(refImageBytes));
            }

            if (posesMap != null && !posesMap.isEmpty()) {
                ObjectNode posesNode = root.putObject("poses_base64");
                for (Map.Entry<String, byte[]> entry : posesMap.entrySet()) {
                    if (entry.getValue() != null) {
                        posesNode.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
                    }
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sandboxUrl.replaceAll("/+$", "") + "/render"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode resJson = objectMapper.readTree(response.body());
                if (resJson.path("success").asBoolean(false)) {
                    String zipB64 = resJson.path("zip_base64").asText("");
                    String prevB64 = resJson.path("preview_image_base64").asText("");
                    JsonNode manifestNode = resJson.path("manifest");
                    byte[] zipBytes = Base64.getDecoder().decode(zipB64);
                    byte[] prevBytes = Base64.getDecoder().decode(prevB64);
                    int fileCount = manifestNode.path("file_count").asInt(1);

                    Map<String, byte[]> posesPreview = new java.util.HashMap<>();
                    JsonNode posesNode = resJson.path("poses_preview_base64");
                    if (posesNode.isObject()) {
                        posesNode.fields().forEachRemaining(entry -> {
                            if (!entry.getValue().asText().isBlank()) {
                                posesPreview.put(entry.getKey(), Base64.getDecoder().decode(entry.getValue().asText()));
                            }
                        });
                    }

                    return RenderResult.builder()
                            .success(true)
                            .zipBytes(zipBytes)
                            .previewBytes(prevBytes)
                            .manifestJson(objectMapper.writeValueAsString(manifestNode))
                            .fileCount(fileCount)
                            .posesPreviewBytes(posesPreview)
                            .build();
                }
            }
            log.warn("Python sandbox returned status {}: {}. Using built-in Java procedural generator.", response.statusCode(), response.body());
        } catch (Exception e) {
            log.warn("Cannot reach Python sandbox at {}: {}. Using built-in Java procedural fallback.", sandboxUrl, e.getMessage());
        }

        return createJavaFallbackPackage(category, styleSpecJson, refImageBytes);
    }

    private RenderResult createJavaFallbackPackage(String category, String styleSpecJson, byte[] refImage) {
        try {
            ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(zipBaos);

            byte[] previewPng = createSimpleCardPng("PREVIEW", new Color(212, 175, 55));
            zos.putNextEntry(new ZipEntry("PREVIEW.png"));
            zos.write(previewPng);
            zos.closeEntry();

            byte[] backPng = createSimpleCardPng("BACK", new Color(30, 15, 60));
            zos.putNextEntry(new ZipEntry("BACK.png"));
            zos.write(backPng);
            zos.closeEntry();

            int count = 2;
            if ("CARD_SKIN".equalsIgnoreCase(category)) {
                String[] suits = {"S", "H", "D", "C"};
                String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
                for (String s : suits) {
                    for (String r : ranks) {
                        Color col = (s.equals("H") || s.equals("D")) ? new Color(220, 50, 80) : new Color(240, 240, 240);
                        byte[] cardBytes = createSimpleCardPng(r + s, col);
                        zos.putNextEntry(new ZipEntry(r + s + ".png"));
                        zos.write(cardBytes);
                        zos.closeEntry();
                        count++;
                    }
                }
            } else if ("DEALER".equalsIgnoreCase(category)) {
                String[] poses = {"idle", "deal", "flip", "win", "avatar_preview"};
                for (String p : poses) {
                    byte[] pBytes = createSimpleCardPng(p.toUpperCase(), new Color(245, 158, 11));
                    zos.putNextEntry(new ZipEntry(p + ".png"));
                    zos.write(pBytes);
                    zos.closeEntry();
                    count++;
                }
            }

            String manifest = "{\"category\":\"" + category + "\",\"file_count\":" + count + ",\"primary_preview\":\"PREVIEW.png\"}";
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
            zos.close();

            return RenderResult.builder()
                    .success(true)
                    .zipBytes(zipBaos.toByteArray())
                    .previewBytes(previewPng)
                    .manifestJson(manifest)
                    .fileCount(count)
                    .build();

        } catch (Exception e) {
            log.error("Failed to build fallback asset package: {}", e.getMessage());
            return RenderResult.builder().success(false).build();
        }
    }

    private byte[] createSimpleCardPng(String label, Color color) throws Exception {
        BufferedImage img = new BufferedImage(300, 420, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(15, 8, 30));
        g.fillRoundRect(0, 0, 300, 420, 24, 24);
        g.setColor(color);
        g.setStroke(new BasicStroke(4));
        g.drawRoundRect(2, 2, 296, 416, 22, 22);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.drawString(label, 40, 210);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
