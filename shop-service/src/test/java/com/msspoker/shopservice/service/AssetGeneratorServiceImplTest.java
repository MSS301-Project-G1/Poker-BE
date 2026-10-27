package com.msspoker.shopservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.ai.RenderScriptPromptBuilder;
import com.msspoker.shopservice.ai.client.AssetSandboxClient;
import com.msspoker.shopservice.ai.provider.AiModelProvider;
import com.msspoker.shopservice.ai.provider.AiProviderRegistry;
import com.msspoker.shopservice.dto.request.AssetPromptOptions;
import com.msspoker.shopservice.dto.response.AssetGenerationBatchResponse;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.entity.AssetGenerationBatch;
import com.msspoker.shopservice.enums.AiProviderType;
import com.msspoker.shopservice.repository.AiModelConfigRepository;
import com.msspoker.shopservice.repository.AiPromptPresetRepository;
import com.msspoker.shopservice.repository.AssetGenerationBatchRepository;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.mapper.AssetGeneratorMapper;
import com.msspoker.shopservice.service.impl.AssetGeneratorServiceImpl;
import com.msspoker.shopservice.util.MetadataJsonMerger;
import com.msspoker.shopservice.validator.AssetGeneratorValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetGeneratorServiceImplTest {

    @Mock
    private AiProviderRegistry providerRegistry;

    @Mock
    private AiModelConfigRepository modelConfigRepository;

    @Mock
    private AiPromptPresetRepository promptPresetRepository;

    @Mock
    private AssetGenerationBatchRepository batchRepository;

    @Mock
    private RenderScriptPromptBuilder promptBuilder;

    @Mock
    private AssetSandboxClient sandboxClient;

    @Mock
    private S3StorageService storageService;

    @Mock
    private ProductService productService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSoundService productSoundService;

    @Mock
    private CardDeckSkinService cardDeckSkinService;

    @Mock
    private MetadataJsonMerger metadataJsonMerger;

    @Mock
    private AiModelProvider aiProvider;

    @Spy
    private AssetGeneratorMapper generatorMapper = org.mapstruct.factory.Mappers.getMapper(AssetGeneratorMapper.class);

    @Spy
    private AssetGeneratorValidator generatorValidator = new com.msspoker.shopservice.validator.impl.AssetGeneratorValidatorImpl();

    @InjectMocks
    private AssetGeneratorServiceImpl service;

    private AiModelConfig defaultModel;

    @BeforeEach
    void setUp() {
        defaultModel = AiModelConfig.builder()
                .id(UUID.randomUUID())
                .provider(AiProviderType.GEMINI)
                .modelName("gemini-1.5-pro")
                .displayName("Gemini 1.5 Pro")
                .isActive(true)
                .isDefault(true)
                .build();
    }

    @Test
    @DisplayName("generateAssets: sinh thành công gói tài nguyên CARD_SKIN và upload S3")
    void generateAssets_CardSkin_Success() {
        AssetPromptOptions options = AssetPromptOptions.builder()
                .extraDescription("Bộ bài Rồng Hoàng Gia")
                .build();

        when(modelConfigRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultModel));
        when(providerRegistry.getRequiredProvider(AiProviderType.GEMINI)).thenReturn(aiProvider);
        when(promptBuilder.buildPrompt(anyString(), any(), any())).thenReturn("Generated Prompt");
        when(aiProvider.generateStyleSpec(any(), anyString(), any(), any())).thenReturn("{\"theme\":\"ROYAL_DRAGON\"}");

        AssetSandboxClient.RenderResult renderResult = AssetSandboxClient.RenderResult.builder()
                .success(true)
                .zipBytes(new byte[]{1, 2, 3})
                .previewBytes(new byte[]{4, 5})
                .manifestJson("{\"file_count\":53}")
                .fileCount(53)
                .build();
        when(sandboxClient.render(eq("CARD_SKIN"), anyString(), any(), any())).thenReturn(renderResult);
        when(storageService.uploadFile(anyString(), any(), anyString())).thenReturn("http://s3/test.zip");
        when(batchRepository.save(any(AssetGenerationBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetGenerationBatchResponse res = service.generateAssets("CARD_SKIN", options, null, null, "admin");

        assertThat(res).isNotNull();
        assertThat(res.categoryCode()).isEqualTo("CARD_SKIN");
        assertThat(res.status()).isEqualTo("GENERATED");
        assertThat(res.fileCount()).isEqualTo(53);
        verify(storageService).uploadFile(anyString(), eq(renderResult.zipBytes()), eq("application/zip"));
        verify(storageService).uploadFile(anyString(), eq(renderResult.previewBytes()), eq("image/png"));
    }

    @Test
    @DisplayName("deleteBatch: xóa thư mục S3 và cập nhật status=DELETED")
    void deleteBatch_Success() {
        UUID batchId = UUID.randomUUID();
        AssetGenerationBatch batch = AssetGenerationBatch.builder()
                .id(batchId)
                .status("GENERATED")
                .build();

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        service.deleteBatch(batchId);

        verify(storageService).deleteFolder("generated/" + batchId + "/");
        assertThat(batch.getStatus()).isEqualTo("DELETED");
        verify(batchRepository).save(batch);
    }
}
