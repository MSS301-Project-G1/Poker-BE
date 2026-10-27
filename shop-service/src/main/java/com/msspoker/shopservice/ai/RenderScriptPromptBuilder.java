package com.msspoker.shopservice.ai;

import com.msspoker.shopservice.dto.request.AssetPromptOptions;
import com.msspoker.shopservice.entity.AiPromptPreset;
import org.springframework.stereotype.Component;

@Component
public class RenderScriptPromptBuilder {

    public String buildPrompt(String categoryCode, AssetPromptOptions options, AiPromptPreset preset) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert game cosmetic designer for an elite online Texas Hold'em poker platform.\n");
        sb.append("Task: Analyze the request and return ONLY a valid JSON Style Spec object for rendering.\n\n");

        sb.append("TARGET ASSET CATEGORY: ").append(categoryCode != null ? categoryCode : "CARD_SKIN").append("\n");

        if (preset != null) {
            sb.append("PRESET THEME: ").append(preset.getPresetName()).append("\n");
            if (preset.getDescription() != null) {
                sb.append("PRESET DESCRIPTION: ").append(preset.getDescription()).append("\n");
            }
            if (preset.getStyleGuideJson() != null) {
                sb.append("BASE STYLE GUIDE: ").append(preset.getStyleGuideJson()).append("\n");
            }
        }

        if (options != null) {
            sb.append("\nADMIN CUSTOM OVERRIDES:\n");
            if (options.primaryColor() != null && !options.primaryColor().isBlank()) {
                sb.append("- Primary Color: ").append(options.primaryColor().trim()).append("\n");
            }
            if (options.secondaryColor() != null && !options.secondaryColor().isBlank()) {
                sb.append("- Secondary Color: ").append(options.secondaryColor().trim()).append("\n");
            }
            if (options.accentColor() != null && !options.accentColor().isBlank()) {
                sb.append("- Accent Color: ").append(options.accentColor().trim()).append("\n");
            }
            if (options.moodTags() != null && !options.moodTags().isEmpty()) {
                sb.append("- Mood & Aesthetic Tags: ").append(String.join(", ", options.moodTags())).append("\n");
            }
            if (options.extraDescription() != null && !options.extraDescription().isBlank()) {
                sb.append("- Extra Admin Description: ").append(options.extraDescription().trim()).append("\n");
            }
            if (options.avoidElements() != null && !options.avoidElements().isBlank()) {
                sb.append("- Elements to AVOID: ").append(options.avoidElements().trim()).append("\n");
            }
            if (options.sourceFidelity() != null) {
                sb.append("- Reference Image Fidelity: ").append(options.sourceFidelity()).append("%\n");
            }
        }

        sb.append("\nJSON SPEC FORMAT REQUIREMENTS:\n");
        sb.append("{\n");
        sb.append("  \"theme\": \"STRING_IDENTIFIER\",\n");
        sb.append("  \"palette\": {\n");
        sb.append("    \"primary\": \"#HEX\",\n");
        sb.append("    \"secondary\": \"#HEX\",\n");
        sb.append("    \"accent\": \"#HEX\",\n");
        sb.append("    \"background\": \"#HEX\",\n");
        sb.append("    \"card_bg\": \"#HEX\"\n");
        sb.append("  },\n");
        sb.append("  \"card_skin\": {\n");
        sb.append("    \"border_radius\": 24,\n");
        sb.append("    \"pip_style\": \"MODERN_GOLD | NEON | MINIMALIST\"\n");
        sb.append("  },\n");
        sb.append("  \"dealer\": {\n");
        sb.append("    \"outfit\": \"STRING\",\n");
        sb.append("    \"poses\": [\"idle\", \"deal\", \"flip\", \"win\"]\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("Output STRICTLY raw JSON. Do not include markdown code block ticks or any conversational text.");

        return sb.toString();
    }
}
