package com.msspoker.shopservice.dto.response;

import lombok.Builder;
import java.util.Map;

@Builder
public record CardDeckManifest(
        String skinType,
        String version,
        String cardBackUrl,
        String previewUrl,
        AtlasInfo atlas,
        Map<String, String> cards
) {
    @Builder
    public record AtlasInfo(
            String textureUrl,
            String coordinatesUrl
    ) {}
}
