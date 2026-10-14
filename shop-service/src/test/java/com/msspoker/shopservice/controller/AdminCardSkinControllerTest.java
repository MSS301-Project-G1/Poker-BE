package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.response.CardDeckManifest;
import com.msspoker.shopservice.dto.response.CardSkinUploadResponse;
import com.msspoker.shopservice.service.CardDeckSkinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCardSkinControllerTest {

    @Mock
    private CardDeckSkinService cardDeckSkinService;

    @InjectMocks
    private AdminCardSkinController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/admin/products/{id}/deck-skin/upload-zip: Tải lên file ZIP thành công")
    void uploadDeckSkinZip_success() throws Exception {
        UUID productId = UUID.randomUUID();
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "cards.zip", "application/zip", "dummy-zip-content".getBytes()
        );

        CardSkinUploadResponse mockResponse = CardSkinUploadResponse.builder()
                .productId(productId)
                .productName("Bộ Bài Hoàng Gia Hắc Kim")
                .totalCardsUploaded(53)
                .version("v1728900000")
                .previewUrl("http://localhost:9000/poker-assets/skins/cards/preview.png")
                .textureAtlasUrl("http://localhost:9000/poker-assets/skins/cards/deck_atlas.png")
                .coordinatesUrl("http://localhost:9000/poker-assets/skins/cards/deck_atlas.json")
                .manifest(CardDeckManifest.builder()
                        .skinType("CARD_DECK_53")
                        .version("v1728900000")
                        .cards(Map.of("2H", "url-2H", "BACK", "url-BACK"))
                        .build())
                .build();

        when(cardDeckSkinService.uploadDeckSkinZip(eq(productId), any())).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/admin/products/{id}/deck-skin/upload-zip", productId)
                        .file(zipFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHOP_SUCCESS"))
                .andExpect(jsonPath("$.data.productId").value(productId.toString()))
                .andExpect(jsonPath("$.data.totalCardsUploaded").value(53))
                .andExpect(jsonPath("$.data.textureAtlasUrl").value("http://localhost:9000/poker-assets/skins/cards/deck_atlas.png"));
    }
}
