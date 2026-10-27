package com.msspoker.shopservice.ai.provider;

import com.msspoker.shopservice.dto.response.AiModelDescriptor;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiProviderType;

import java.util.List;
import java.util.Map;

/**
 * Interface thống nhất cho tất cả các nhà cung cấp AI (Google Gemini, OpenAI, Claude, v.v.).
 * Cung cấp:
 * 1. Liệt kê danh sách model khả dụng từ provider (dùng để đồng bộ vào hệ thống).
 * 2. Phân tích ảnh và prompt để sinh Style Spec JSON cho Python Asset Sandbox render.
 * 3. Tạo 4 tư thế Dealer (idle, deal, flip, win) từ 1 ảnh tham chiếu nhân vật (sử dụng Gemini Multimodal / Imagen).
 * 4. Tinh chỉnh Style Spec dựa trên feedback phản hồi từ Admin.
 */
public interface AiModelProvider {

    AiProviderType getProviderType();

    List<AiModelDescriptor> listAvailableModels();

    String generateStyleSpec(AiModelConfig config, String prompt, byte[] referenceImage, String referenceImageMimeType);

    Map<String, byte[]> generateDealerPoses(AiModelConfig config, byte[] referencePhoto, String referenceImageMimeType, String characterDescription);

    String refineStyleSpec(AiModelConfig config, String currentSpec, String userFeedback);
}
