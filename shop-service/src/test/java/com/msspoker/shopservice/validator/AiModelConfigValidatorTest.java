package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.request.UpdateAiModelConfigRequest;
import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiProviderType;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.repository.AiModelConfigRepository;
import com.msspoker.shopservice.validator.impl.AiModelConfigValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelConfigValidatorTest {

    @Mock
    private AiModelConfigRepository configRepository;

    @InjectMocks
    private AiModelConfigValidatorImpl validator;

    private UUID modelId;
    private AiModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        modelId = UUID.randomUUID();
        modelConfig = AiModelConfig.builder()
                .id(modelId)
                .provider(AiProviderType.GEMINI)
                .modelName("gemini-1.5-pro")
                .displayName("Gemini 1.5 Pro")
                .isActive(true)
                .isDefault(false)
                .build();
    }

    @Test
    @DisplayName("validateExists: thành công nếu entity tồn tại")
    void validateExists_Success() {
        assertThatCode(() -> validator.validateExists(modelId, modelConfig))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateExists: ném ngoại lệ nếu entity null")
    void validateExists_NotFound() {
        assertThatThrownBy(() -> validator.validateExists(modelId, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không tìm thấy cấu hình");
    }

    @Test
    @DisplayName("validateForCreate: thành công nếu model chưa tồn tại")
    void validateForCreate_Success() {
        CreateAiModelConfigRequest req = CreateAiModelConfigRequest.builder()
                .provider(AiProviderType.GEMINI)
                .modelName("gemini-1.5-flash")
                .displayName("Gemini Flash")
                .build();

        when(configRepository.findByProviderAndModelName(AiProviderType.GEMINI, "gemini-1.5-flash"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> validator.validateForCreate(req))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateForCreate: ném ngoại lệ nếu model đã tồn tại")
    void validateForCreate_Duplicate() {
        CreateAiModelConfigRequest req = CreateAiModelConfigRequest.builder()
                .provider(AiProviderType.GEMINI)
                .modelName("gemini-1.5-pro")
                .displayName("Gemini Pro")
                .build();

        when(configRepository.findByProviderAndModelName(AiProviderType.GEMINI, "gemini-1.5-pro"))
                .thenReturn(Optional.of(modelConfig));

        assertThatThrownBy(() -> validator.validateForCreate(req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã tồn tại trong hệ thống");
    }

    @Test
    @DisplayName("validateForDelete: ném ngoại lệ nếu xóa model default")
    void validateForDelete_CannotDeleteDefault() {
        AiModelConfig defaultModel = AiModelConfig.builder()
                .id(modelId)
                .provider(AiProviderType.GEMINI)
                .modelName("gemini-1.5-pro")
                .isDefault(true)
                .build();

        assertThatThrownBy(() -> validator.validateForDelete(modelId, defaultModel))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không thể xóa mô hình AI đang được chỉ định làm mặc định");
    }
}
