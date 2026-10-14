package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.CardDeckManifest;
import com.msspoker.shopservice.dto.response.CardSkinUploadResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.service.CardDeckSkinService;
import com.msspoker.shopservice.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private CardDeckSkinService cardDeckSkinService;

    @InjectMocks
    private AdminProductController controller;

    @Test
    @DisplayName("createProduct gọi ProductService và trả về 201 Created")
    void createProduct() {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(UUID.randomUUID())
                .name("Khung Rồng")
                .tier(ProductTier.VIP)
                .priceVnd(50000L)
                .assetUrl("https://example.com/frame.png")
                .build();
        ProductResponse response = ProductResponse.builder().name("Khung Rồng").build();

        when(productService.createProduct(request)).thenReturn(response);

        ResponseEntity<ApiResponse<ProductResponse>> result = controller.createProduct(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(productService).createProduct(request);
    }

    @Test
    @DisplayName("updateProduct gọi ProductService và trả về 200 OK")
    void updateProduct() {
        UUID id = UUID.randomUUID();
        UpdateProductRequest request = UpdateProductRequest.builder()
                .categoryId(UUID.randomUUID())
                .name("Khung Rồng Update")
                .tier(ProductTier.VIP)
                .priceVnd(60000L)
                .assetUrl("https://example.com/frame.png")
                .build();
        ProductResponse response = ProductResponse.builder().name("Khung Rồng Update").build();

        when(productService.updateProduct(id, request)).thenReturn(response);

        ResponseEntity<ApiResponse<ProductResponse>> result = controller.updateProduct(id, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(productService).updateProduct(id, request);
    }

    @Test
    @DisplayName("deleteProduct gọi ProductService và trả về 200 OK")
    void deleteProduct() {
        UUID id = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> result = controller.deleteProduct(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(productService).deleteProduct(id);
    }

    @Test
    @DisplayName("uploadDeckSkinZip gọi CardDeckSkinService và trả về kết quả")
    void uploadDeckSkinZip() {
        UUID productId = UUID.randomUUID();
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "cards.zip", "application/zip", "dummy-zip-content".getBytes()
        );

        CardSkinUploadResponse mockResponse = CardSkinUploadResponse.builder()
                .productId(productId)
                .productName("Bộ Bài Hoàng Gia Hắc Kim")
                .totalCardsUploaded(53)
                .version("v1")
                .previewUrl("http://localhost:9000/poker-assets/skins/cards/preview.png")
                .textureAtlasUrl("http://localhost:9000/poker-assets/skins/cards/deck_atlas.png")
                .coordinatesUrl("http://localhost:9000/poker-assets/skins/cards/deck_atlas.json")
                .manifest(CardDeckManifest.builder()
                        .skinType("CARD_DECK_53")
                        .version("v1")
                        .cards(Map.of("2H", "url-2H", "BACK", "url-BACK"))
                        .build())
                .build();

        when(cardDeckSkinService.uploadDeckSkinZip(eq(productId), any())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<CardSkinUploadResponse>> result = controller.uploadDeckSkinZip(productId, zipFile);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData().productId()).isEqualTo(productId);
        assertThat(result.getBody().getData().totalCardsUploaded()).isEqualTo(53);
        verify(cardDeckSkinService).uploadDeckSkinZip(eq(productId), any());
    }

    @Test
    @DisplayName("uploadDeckSkinFiles gọi CardDeckSkinService và trả về kết quả")
    void uploadDeckSkinFiles() {
        UUID productId = UUID.randomUUID();
        MockMultipartFile file1 = new MockMultipartFile("files", "2H.png", "image/png", "dummy".getBytes());
        List<MockMultipartFile> files = List.of(file1);

        CardSkinUploadResponse mockResponse = CardSkinUploadResponse.builder()
                .productId(productId)
                .totalCardsUploaded(53)
                .build();

        when(cardDeckSkinService.uploadDeckSkinFiles(eq(productId), any())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<CardSkinUploadResponse>> result = controller.uploadDeckSkinFiles(productId, (List) files);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData().productId()).isEqualTo(productId);
        verify(cardDeckSkinService).uploadDeckSkinFiles(eq(productId), any());
    }
}
