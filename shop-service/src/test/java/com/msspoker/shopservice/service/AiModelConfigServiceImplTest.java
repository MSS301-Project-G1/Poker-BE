package com.msspoker.shopservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.ai.provider.AiModelProvider;
import com.msspoker.shopservice.ai.provider.AiProviderRegistry;
import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.request.UpdateAiModelConfigRequest;
import com.msspoker.shopservice.dto.response.AiModelConfigResponse;
import com.msspoker.shopservice.dto.response.AiModelDescriptor;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiModelCapability;
import com.msspoker.shopservice.enums.AiProviderType;
import com.msspoker.shopservice.repository.AiModelConfigRepository;
import com.msspoker.shopservice.service.impl.AiModelConfigServiceImpl;
import org.mapstruct.factory.Mappers;
import com.msspoker.shopservice.mapper.AiModelConfigMapper;
import com.msspoker.shopservice.validator.AiModelConfigValidator;
import com.msspoker.shopservice.validator.impl.AiModelConfigValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelConfigServiceImplTest {

    @Mock
    private AiModelConfigRepository configRepository;

    @Mock
    private AiProviderRegistry providerRegistry;

    @Mock
    private AiModelProvider geminiProvider;

    @Spy
    private AiModelConfigMapper modelConfigMapper = Mappers.getMapper(AiModelConfigMapper.class);

    private AiModelConfigValidator modelConfigValidator;
    private AiModelConfigServiceImpl service;

    private UUID configId;
    private AiModelConfig activeConfig;

    @BeforeEach
    void setUp() {
        modelConfigValidator = new AiModelConfigValidatorImpl(configRepository);
        service = new AiModelConfigServiceImpl(
                configRepository,
                providerRegistry,
                modelConfigMapper,
                modelConfigValidator
        );

        configId = UUID.randomUUID();
        activeConfig = AiModelConfig.builder()
                .id(configId)
                .provider(AiProviderType.GEMINI)
                .modelName("gemini-1.5-pro")
                .displayName("Gemini 1.5 Pro")
                .capabilities("[\"STYLE_SPEC_GENERATION\"]")
                .isActive(true)
                .isDefault(true)
                .build();
    }

    @Test
    @DisplayName("getActiveConfigs: trả về danh sách model đang active và parse capabilities json")
    void getActiveConfigs_Success() {
        when(configRepository.findByIsActiveTrue()).thenReturn(List.of(activeConfig));

        List<AiModelConfigResponse> result = service.getActiveConfigs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).modelName()).isEqualTo("gemini-1.5-pro");
        assertThat(result.get(0).capabilities()).contains("STYLE_SPEC_GENERATION");
    }

    @Test
    @DisplayName("createConfig: thành công và clear default nếu request.isDefault = true")
    void createConfig_Success() {
        CreateAiModelConfigRequest req = CreateAiModelConfigRequest.builder()
                .provider(AiProviderType.GEMINI)
                .modelName("gemini-1.5-flash")
                .displayName("Gemini 1.5 Flash")
                .capabilities(List.of("STYLE_SPEC_GENERATION"))
                .isActive(true)
                .isDefault(true)
                .build();

        when(configRepository.findByProviderAndModelName(AiProviderType.GEMINI, "gemini-1.5-flash"))
                .thenReturn(Optional.empty());
        when(configRepository.save(any(AiModelConfig.class))).thenAnswer(inv -> {
            AiModelConfig c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        AiModelConfigResponse res = service.createConfig(req);

        assertThat(res.modelName()).isEqualTo("gemini-1.5-flash");
        assertThat(res.isDefault()).isTrue();
        verify(configRepository).clearDefaultExcept(res.id());
    }

    @Test
    @DisplayName("createConfig: ném lỗi nếu model trùng lặp cùng provider")
    void createConfig_DuplicateThrows() {
        CreateAiModelConfigRequest req = CreateAiModelConfigRequest.builder()
                .provider(AiProviderType.GEMINI)
                .modelName("gemini-1.5-pro")
                .displayName("Gemini Pro")
                .build();

        when(configRepository.findByProviderAndModelName(AiProviderType.GEMINI, "gemini-1.5-pro"))
                .thenReturn(Optional.of(activeConfig));

        assertThatThrownBy(() -> service.createConfig(req))
                .isInstanceOf(com.msspoker.shopservice.exception.AppException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("setDefaultConfig: cập nhật cờ default và gọi clearDefaultExcept")
    void setDefaultConfig_Success() {
        when(configRepository.findById(configId)).thenReturn(Optional.of(activeConfig));
        when(configRepository.save(any(AiModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AiModelConfigResponse res = service.setDefaultConfig(configId);

        assertThat(res.isDefault()).isTrue();
        verify(configRepository).clearDefaultExcept(configId);
    }

    @Test
    @DisplayName("toggleActive: không cho phép tắt active đối với model đang là default")
    void toggleActive_CannotDeactivateDefault() {
        when(configRepository.findById(configId)).thenReturn(Optional.of(activeConfig));

        assertThatThrownBy(() -> service.toggleActive(configId, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không thể vô hiệu hóa mô hình mặc định");
    }

    @Test
    @DisplayName("syncFromProvider: đồng bộ model từ Provider vào DB")
    void syncFromProvider_Success() {
        when(providerRegistry.getRequiredProvider(AiProviderType.GEMINI)).thenReturn(geminiProvider);
        when(geminiProvider.listAvailableModels()).thenReturn(List.of(
                AiModelDescriptor.builder()
                        .provider(AiProviderType.GEMINI)
                        .modelName("gemini-1.5-pro")
                        .displayName("Google Gemini 1.5 Pro (Updated)")
                        .capabilities(List.of(AiModelCapability.STYLE_SPEC_GENERATION))
                        .build()
        ));
        when(configRepository.findByProviderAndModelName(AiProviderType.GEMINI, "gemini-1.5-pro"))
                .thenReturn(Optional.of(activeConfig));
        when(configRepository.save(any(AiModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        List<AiModelConfigResponse> synced = service.syncFromProvider(AiProviderType.GEMINI);

        assertThat(synced).hasSize(1);
        assertThat(synced.get(0).displayName()).isEqualTo("Google Gemini 1.5 Pro (Updated)");
    }
}
