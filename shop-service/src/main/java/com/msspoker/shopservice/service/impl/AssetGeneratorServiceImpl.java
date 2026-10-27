package com.msspoker.shopservice.service.impl;

import com.github.f4b6a3.uuid.UuidCreator;
import com.msspoker.shopservice.ai.RenderScriptPromptBuilder;
import com.msspoker.shopservice.ai.client.AssetSandboxClient;
import com.msspoker.shopservice.ai.provider.AiModelProvider;
import com.msspoker.shopservice.ai.provider.AiProviderRegistry;
import com.msspoker.shopservice.dto.request.AssetPromptOptions;
import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.response.AssetGenerationBatchResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.entity.AiPromptPreset;
import com.msspoker.shopservice.entity.AssetGenerationBatch;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.exception.AssetGeneratorExceptions;
import com.msspoker.shopservice.mapper.AssetGeneratorMapper;
import com.msspoker.shopservice.repository.AiModelConfigRepository;
import com.msspoker.shopservice.repository.AiPromptPresetRepository;
import com.msspoker.shopservice.repository.AssetGenerationBatchRepository;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.AssetGeneratorService;
import com.msspoker.shopservice.service.CardDeckSkinService;
import com.msspoker.shopservice.service.ProductService;
import com.msspoker.shopservice.service.S3StorageService;
import com.msspoker.shopservice.util.InMemoryMultipartFile;
import com.msspoker.shopservice.util.MetadataJsonMerger;
import com.msspoker.shopservice.validator.AssetGeneratorValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetGeneratorServiceImpl implements AssetGeneratorService {

    private final AiProviderRegistry providerRegistry;
    private final AiModelConfigRepository modelConfigRepository;
    private final AiPromptPresetRepository promptPresetRepository;
    private final AssetGenerationBatchRepository batchRepository;
    private final RenderScriptPromptBuilder promptBuilder;
    private final AssetSandboxClient sandboxClient;
    private final S3StorageService storageService;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CardDeckSkinService cardDeckSkinService;
    private final MetadataJsonMerger metadataJsonMerger;
    private final AssetGeneratorMapper generatorMapper;
    private final AssetGeneratorValidator generatorValidator;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    @Transactional
    public AssetGenerationBatchResponse generateAssets(
            String categoryCode,
            AssetPromptOptions options,
            MultipartFile referenceImage,
            MultipartFile soundFile,
            String createdBy
    ) {
        String effectiveCategory = (categoryCode != null && !categoryCode.isBlank()) ? categoryCode.toUpperCase() : "CARD_SKIN";
        log.info("Generating assets for category='{}', requested by='{}'", effectiveCategory, createdBy);

        generatorValidator.validateGenerateRequest(effectiveCategory, options, referenceImage);

        // 1. Resolve Prompt Preset
        AiPromptPreset preset = resolvePreset(effectiveCategory, options);

        // 2. Resolve AI Model Config
        AiModelConfig modelConfig = resolveModelConfig(options);

        // 3. Assemble Prompt & Generate Style Spec
        String prompt = promptBuilder.buildPrompt(effectiveCategory, options, preset);
        AiModelProvider provider = providerRegistry.getRequiredProvider(modelConfig.getProvider());

        byte[] refBytes = null;
        String refMime = null;
        if (referenceImage != null && !referenceImage.isEmpty()) {
            try {
                refBytes = referenceImage.getBytes();
                refMime = referenceImage.getContentType();
            } catch (Exception e) {
                log.warn("Failed to read reference image bytes: {}", e.getMessage());
            }
        }

        String styleSpec = provider.generateStyleSpec(modelConfig, prompt, refBytes, refMime);

        // 4. Generate Dealer Poses if DEALER category
        Map<String, byte[]> posesMap = null;
        if ("DEALER".equalsIgnoreCase(effectiveCategory)) {
            String charDesc = (options != null && options.extraDescription() != null) ? options.extraDescription() : "Professional live dealer";
            posesMap = provider.generateDealerPoses(modelConfig, refBytes, refMime, charDesc);
        }

        // 5. Render Package via Python Sandbox (or resilient fallback)
        AssetSandboxClient.RenderResult renderResult = sandboxClient.render(effectiveCategory, styleSpec, refBytes, posesMap);
        if (!renderResult.success() || renderResult.zipBytes() == null || renderResult.zipBytes().length == 0) {
            throw AssetGeneratorExceptions.renderFailed("Quá trình kết xuất tài nguyên đồ họa thất bại. Vui lòng kiểm tra tham số!");
        }

        // 6. Upload Generated Package & Preview to S3
        UUID batchId = UuidCreator.getTimeOrderedEpoch();
        String zipKey = "generated/" + batchId + "/package.zip";
        String previewKey = "generated/" + batchId + "/preview.png";

        String s3ZipUrl = storageService.uploadFile(zipKey, renderResult.zipBytes(), "application/zip");
        String s3PreviewUrl = storageService.uploadFile(previewKey, renderResult.previewBytes(), "image/png");

        Map<String, String> poseUrls = new HashMap<>();
        if (renderResult.posesPreviewBytes() != null && !renderResult.posesPreviewBytes().isEmpty()) {
            for (Map.Entry<String, byte[]> entry : renderResult.posesPreviewBytes().entrySet()) {
                if (entry.getValue() != null && entry.getValue().length > 0) {
                    String poseKey = "generated/" + batchId + "/poses/" + entry.getKey() + ".png";
                    String pUrl = storageService.uploadFile(poseKey, entry.getValue(), "image/png");
                    poseUrls.put(entry.getKey(), pUrl);
                }
            }
        }
        poseUrls.put("avatar", s3PreviewUrl);

        // 7. Save Batch Record
        AssetGenerationBatch batch = AssetGenerationBatch.builder()
                .id(batchId)
                .categoryCode(effectiveCategory)
                .modelConfig(modelConfig)
                .rawPrompt(prompt)
                .styleSpec(styleSpec)
                .s3ZipUrl(s3ZipUrl)
                .s3PreviewUrl(s3PreviewUrl)
                .status("GENERATED")
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        AssetGenerationBatch saved = batchRepository.save(batch);

        return generatorMapper.toResponse(saved, renderResult.fileCount(), poseUrls);
    }

    @Override
    @Transactional
    public AssetGenerationBatchResponse refineBatch(UUID batchId, String feedback) {
        AssetGenerationBatch batch = batchRepository.findById(batchId).orElse(null);
        generatorValidator.validateForRefine(batch, feedback);

        AiModelConfig modelConfig = batch.getModelConfig() != null
                ? batch.getModelConfig()
                : resolveModelConfig(null);

        AiModelProvider provider = providerRegistry.getRequiredProvider(modelConfig.getProvider());
        String refinedSpec = provider.refineStyleSpec(modelConfig, batch.getStyleSpec(), feedback);

        AssetSandboxClient.RenderResult renderResult = sandboxClient.render(batch.getCategoryCode(), refinedSpec, null, null);

        String zipKey = "generated/" + batchId + "/package.zip";
        String previewKey = "generated/" + batchId + "/preview.png";

        String s3ZipUrl = storageService.uploadFile(zipKey, renderResult.zipBytes(), "application/zip");
        String s3PreviewUrl = storageService.uploadFile(previewKey, renderResult.previewBytes(), "image/png");

        Map<String, String> poseUrls = new HashMap<>();
        if (renderResult.posesPreviewBytes() != null && !renderResult.posesPreviewBytes().isEmpty()) {
            for (Map.Entry<String, byte[]> entry : renderResult.posesPreviewBytes().entrySet()) {
                if (entry.getValue() != null && entry.getValue().length > 0) {
                    String poseKey = "generated/" + batchId + "/poses/" + entry.getKey() + ".png";
                    String pUrl = storageService.uploadFile(poseKey, entry.getValue(), "image/png");
                    poseUrls.put(entry.getKey(), pUrl);
                }
            }
        }
        poseUrls.put("avatar", s3PreviewUrl);

        batch.setStyleSpec(refinedSpec);
        batch.setS3ZipUrl(s3ZipUrl);
        batch.setS3PreviewUrl(s3PreviewUrl);
        batch.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        return generatorMapper.toResponse(batchRepository.save(batch), renderResult.fileCount(), poseUrls);
    }

    @Override
    @Transactional
    public ProductResponse applyBatchToProduct(
            UUID batchId,
            UUID targetProductId,
            UUID categoryId,
            String productName,
            Long priceVipCoin,
            Long priceVnd
    ) {
        AssetGenerationBatch batch = batchRepository.findById(batchId).orElse(null);
        generatorValidator.validateBatchExists(batchId, batch);

        Product product = null;
        if (targetProductId != null) {
            product = productRepository.findByIdAndIsDeletedFalse(targetProductId).orElse(null);
        }
        generatorValidator.validateForApply(batch, targetProductId, product);

        if (product == null) {
            Category category = null;
            if (categoryId != null) {
                category = categoryRepository.findById(categoryId).orElse(null);
            }
            if (category == null) {
                category = categoryRepository.findByCode(batch.getCategoryCode())
                        .orElseGet(() -> categoryRepository.findAll().stream().findFirst()
                                .orElseThrow(() -> new IllegalStateException("Không tìm thấy danh mục cho mã: " + batch.getCategoryCode())));
            }

            String name = (productName != null && !productName.isBlank())
                    ? productName.trim()
                    : "Vật phẩm AI: " + category.getName() + " #" + batch.getId().toString().substring(0, 6);

            long vipPrice = (priceVipCoin != null && priceVipCoin > 0) ? priceVipCoin : 50L;
            long vndPrice = (priceVnd != null && priceVnd > 0) ? priceVnd : (vipPrice * 1000);

            CreateProductRequest createReq = CreateProductRequest.builder()
                    .categoryId(category.getId())
                    .name(name)
                    .description("Vật phẩm sinh tự động bởi hệ thống AI Generator (" + (batch.getModelConfig() != null ? batch.getModelConfig().getDisplayName() : "Gemini") + ")")
                    .tier(ProductTier.VIP)
                    .priceVnd(vndPrice)
                    .priceVipCoin(vipPrice)
                    .assetUrl(batch.getS3PreviewUrl())
                    .previewVideoUrl(null)
                    .isExclusive(false)
                    .build();

            ProductResponse created = productService.createProduct(createReq);
            product = productRepository.findById(created.id()).orElseThrow();
        }

        // Apply assets
        if ("CARD_SKIN".equalsIgnoreCase(batch.getCategoryCode())) {
            AssetSandboxClient.RenderResult res = sandboxClient.render(batch.getCategoryCode(), batch.getStyleSpec(), null, null);
            InMemoryMultipartFile zipFile = new InMemoryMultipartFile(
                    "file", "deck_" + batch.getId() + ".zip", "application/zip", res.zipBytes()
            );
            cardDeckSkinService.uploadDeckSkinZip(product.getId(), zipFile);
        } else {
            product.setAssetUrl(batch.getS3PreviewUrl());
            product.setPreviewUrl(batch.getS3PreviewUrl());
            String merged = metadataJsonMerger.mergeManifestWithExistingMetadata(product.getMetadata(), batch.getStyleSpec());

            // If DEALER, ensure poses map is embedded in metadata for client playback
            if ("DEALER".equalsIgnoreCase(batch.getCategoryCode())) {
                try {
                    Map<String, String> poses = new HashMap<>();
                    poses.put("idle", storageService.getPublicUrl("generated/" + batch.getId() + "/poses/idle.png"));
                    poses.put("deal", storageService.getPublicUrl("generated/" + batch.getId() + "/poses/deal.png"));
                    poses.put("flip", storageService.getPublicUrl("generated/" + batch.getId() + "/poses/flip.png"));
                    poses.put("win", storageService.getPublicUrl("generated/" + batch.getId() + "/poses/win.png"));
                    poses.put("avatar", batch.getS3PreviewUrl());

                    Map<String, Object> extra = Map.of("poses", poses);
                    String extraJson = objectMapper.writeValueAsString(extra);
                    merged = metadataJsonMerger.merge(merged, extraJson);
                } catch (Exception e) {
                    log.warn("Failed to embed poses map into metadata for batch id={}: {}", batch.getId(), e.getMessage());
                }
            }

            product.setMetadata(merged);
            productRepository.save(product);
        }

        // Mark batch APPLIED
        batch.setStatus("APPLIED");
        batch.setAppliedAt(Instant.now());
        batch.setProductId(product.getId());
        batchRepository.save(batch);

        return productService.getProductDetail(product.getId(), null);
    }

    @Override
    @Transactional
    public void deleteBatch(UUID batchId) {
        AssetGenerationBatch batch = batchRepository.findById(batchId).orElse(null);
        generatorValidator.validateForDelete(batchId, batch);

        try {
            storageService.deleteFolder("generated/" + batchId + "/");
        } catch (Exception e) {
            log.warn("Failed to delete S3 folder for batch id={}: {}", batchId, e.getMessage());
        }

        batch.setStatus("DELETED");
        batchRepository.save(batch);
    }

    @Override
    public List<AssetGenerationBatchResponse> getRecentBatches() {
        return generatorMapper.toResponseList(batchRepository.findTop20ByOrderByCreatedAtDesc());
    }

    @Override
    public String previewPrompt(String categoryCode, AssetPromptOptions options) {
        String cat = (categoryCode != null && !categoryCode.isBlank()) ? categoryCode.toUpperCase() : "CARD_SKIN";
        AiPromptPreset preset = resolvePreset(cat, options);
        return promptBuilder.buildPrompt(cat, options, preset);
    }

    @Override
    public List<AiPromptPreset> getPromptPresets(String categoryCode) {
        if (categoryCode != null && !categoryCode.isBlank()) {
            return promptPresetRepository.findByCategoryCodeAndIsActiveTrue(categoryCode.toUpperCase());
        }
        return promptPresetRepository.findByIsActiveTrue();
    }

    private AiPromptPreset resolvePreset(String categoryCode, AssetPromptOptions options) {
        if (options != null) {
            if (options.presetId() != null) {
                return promptPresetRepository.findById(options.presetId()).orElse(null);
            }
            if (options.presetName() != null && !options.presetName().isBlank()) {
                return promptPresetRepository.findByPresetName(options.presetName().trim()).orElse(null);
            }
        }
        List<AiPromptPreset> presets = promptPresetRepository.findByCategoryCodeAndIsActiveTrue(categoryCode);
        return presets.isEmpty() ? null : presets.get(0);
    }

    private AiModelConfig resolveModelConfig(AssetPromptOptions options) {
        if (options != null && options.modelConfigId() != null) {
            return modelConfigRepository.findById(options.modelConfigId())
                    .orElseGet(this::getDefaultOrFirstActiveModel);
        }
        return getDefaultOrFirstActiveModel();
    }

    private AiModelConfig getDefaultOrFirstActiveModel() {
        return modelConfigRepository.findByIsDefaultTrue()
                .or(() -> modelConfigRepository.findByIsActiveTrue().stream().findFirst())
                .orElse(null);
    }
}
