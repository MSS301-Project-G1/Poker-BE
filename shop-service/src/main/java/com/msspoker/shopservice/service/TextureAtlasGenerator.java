package com.msspoker.shopservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.msspoker.shopservice.exception.CardSkinExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TextureAtlasGenerator {

    private final ObjectMapper objectMapper;

    public record AtlasResult(byte[] imageBytes, byte[] jsonBytes) {}

    /**
     * Ghép danh sách các ảnh lá bài vào 1 tấm Texture Atlas (Sprite Sheet) định dạng PNG trong suốt (ARGB)
     * và xuất ra file JSON tọa độ khung (frames) chuẩn Game Engine (Phaser / Pixi.js / WebGL).
     *
     * @param cardImages Map chứa mã lá bài (key) và dữ liệu byte của ảnh (value)
     * @return AtlasResult chứa byte[] của ảnh PNG và byte[] của JSON coordinates
     */
    public AtlasResult generateAtlas(Map<String, byte[]> cardImages) {
        if (cardImages == null || cardImages.isEmpty()) {
            throw CardSkinExceptions.textureAtlasGenerationError("Không có hình ảnh lá bài nào để tạo Texture Atlas.");
        }

        try {
            // 1. Sắp xếp các lá bài theo thứ tự chuẩn để dễ kiểm tra
            List<String> sortedKeys = new ArrayList<>(cardImages.keySet());
            Collections.sort(sortedKeys);

            // 2. Đọc ảnh mẫu đầu tiên để xác định kích thước tỉ lệ
            BufferedImage sampleImg = null;
            for (String key : sortedKeys) {
                byte[] bytes = cardImages.get(key);
                if (bytes != null && bytes.length > 0) {
                    sampleImg = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (sampleImg != null) {
                        break;
                    }
                }
            }

            if (sampleImg == null) {
                throw CardSkinExceptions.textureAtlasGenerationError("Không thể đọc định dạng hình ảnh của các lá bài.");
            }

            int origW = sampleImg.getWidth();
            int origH = sampleImg.getHeight();

            // Chuẩn hóa kích thước từng ô lá bài trên Atlas (tối đa 240x336 để vừa GPU WebGL texture limit)
            int targetCardW = origW;
            int targetCardH = origH;
            int maxDim = 240;
            if (targetCardW > maxDim) {
                double scale = (double) maxDim / targetCardW;
                targetCardW = maxDim;
                targetCardH = (int) Math.round(origH * scale);
            }

            // Bố trí lưới: 8 cột (Columns). Số hàng = ceil(totalCards / 8.0)
            int totalCards = sortedKeys.size();
            int cols = 8;
            int rows = (int) Math.ceil((double) totalCards / cols);

            int atlasW = cols * targetCardW;
            int atlasH = rows * targetCardH;

            BufferedImage atlasImg = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = atlasImg.createGraphics();

            // Cấu hình chất lượng cao khi vẽ và khử răng cưa
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            ObjectNode framesNode = objectMapper.createObjectNode();

            // 3. Lần lượt vẽ từng lá bài vào lưới và ghi lại tọa độ
            for (int i = 0; i < totalCards; i++) {
                String cardCode = sortedKeys.get(i);
                byte[] cardBytes = cardImages.get(cardCode);

                BufferedImage cardImg = ImageIO.read(new ByteArrayInputStream(cardBytes));
                if (cardImg == null) {
                    continue;
                }

                int col = i % cols;
                int row = i / cols;
                int x = col * targetCardW;
                int y = row * targetCardH;

                g2d.drawImage(cardImg, x, y, targetCardW, targetCardH, null);

                // Ghi nhận tọa độ vào frame JSON
                ObjectNode frameDetail = objectMapper.createObjectNode();
                ObjectNode frameCoord = objectMapper.createObjectNode();
                frameCoord.put("x", x);
                frameCoord.put("y", y);
                frameCoord.put("w", targetCardW);
                frameCoord.put("h", targetCardH);

                frameDetail.set("frame", frameCoord);
                frameDetail.put("rotated", false);
                frameDetail.put("trimmed", false);

                framesNode.set(cardCode, frameDetail);
            }

            g2d.dispose();

            // 4. Tạo metadata của Atlas JSON
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.set("frames", framesNode);

            ObjectNode metaNode = objectMapper.createObjectNode();
            metaNode.put("image", "deck_atlas.png");
            metaNode.put("format", "RGBA8888");
            ObjectNode sizeNode = objectMapper.createObjectNode();
            sizeNode.put("w", atlasW);
            sizeNode.put("h", atlasH);
            metaNode.set("size", sizeNode);
            metaNode.put("totalCards", totalCards);
            rootNode.set("meta", metaNode);

            // 5. Xuất ra byte array
            ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
            ImageIO.write(atlasImg, "png", imgBaos);
            byte[] atlasPngBytes = imgBaos.toByteArray();

            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(rootNode)
                    .getBytes(StandardCharsets.UTF_8);

            log.info("Generated Texture Atlas: {} cards, dimensions {}x{}, PNG size={} KB",
                    totalCards, atlasW, atlasH, atlasPngBytes.length / 1024);

            return new AtlasResult(atlasPngBytes, jsonBytes);

        } catch (IOException e) {
            log.error("Failed to generate Texture Atlas: {}", e.getMessage(), e);
            throw CardSkinExceptions.textureAtlasGenerationError("Lỗi hệ thống khi sinh Texture Atlas: " + e.getMessage());
        }
    }
}
