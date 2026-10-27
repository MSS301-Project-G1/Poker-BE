package com.msspoker.shopservice.ai.provider;

import com.msspoker.shopservice.enums.AiProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderRegistryTest {

    @Mock
    private AiModelProvider geminiProvider;

    @Mock
    private AiModelProvider openAiProvider;

    private AiProviderRegistry registry;

    @BeforeEach
    void setUp() {
        when(geminiProvider.getProviderType()).thenReturn(AiProviderType.GEMINI);
        when(openAiProvider.getProviderType()).thenReturn(AiProviderType.OPENAI);

        registry = new AiProviderRegistry(List.of(geminiProvider, openAiProvider));
    }

    @Test
    @DisplayName("findProvider: tìm thấy đúng provider theo enum")
    void findProvider_Success() {
        Optional<AiModelProvider> found = registry.findProvider(AiProviderType.GEMINI);
        assertThat(found).isPresent().contains(geminiProvider);

        Optional<AiModelProvider> foundOpenAi = registry.findProvider(AiProviderType.OPENAI);
        assertThat(foundOpenAi).isPresent().contains(openAiProvider);
    }

    @Test
    @DisplayName("getRequiredProvider: ném lỗi khi provider không tồn tại")
    void getRequiredProvider_ThrowsWhenNotFound() {
        assertThatThrownBy(() -> registry.getRequiredProvider(AiProviderType.ANTHROPIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy AI Provider");
    }

    @Test
    @DisplayName("getAllProviders: trả về toàn bộ danh sách đã đăng ký")
    void getAllProviders_Success() {
        assertThat(registry.getAllProviders()).hasSize(2);
    }
}
