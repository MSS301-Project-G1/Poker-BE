package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.repository.OrderRepository;
import com.msspoker.shopservice.validator.impl.CoinBundleValidatorImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoinBundleValidatorTest {

    @Mock
    private CoinBundleRepository coinBundleRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CoinBundleValidatorImpl coinBundleValidator;

    @Test
    @DisplayName("validateExists: thành công khi bundle không null")
    void validateExists_Success() {
        CoinBundle bundle = CoinBundle.builder().id(UUID.randomUUID()).build();
        assertThatCode(() -> coinBundleValidator.validateExists(bundle.getId(), bundle))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateExists: ném lỗi khi bundle null")
    void validateExists_Throws() {
        assertThatThrownBy(() -> coinBundleValidator.validateExists(UUID.randomUUID(), null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validateForCreate: ném lỗi khi mã bundle đã tồn tại")
    void validateForCreate_DuplicateCodeThrows() {
        CreateCoinBundleRequest request = CreateCoinBundleRequest.builder().code("BUNDLE_100K").build();
        when(coinBundleRepository.existsByCode("BUNDLE_100K")).thenReturn(true);

        assertThatThrownBy(() -> coinBundleValidator.validateForCreate(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("validateForSandboxPurchase: ném lỗi khi bundle null hoặc ngừng mở bán")
    void validateForSandboxPurchase_InactiveThrows() {
        assertThatThrownBy(() -> coinBundleValidator.validateForSandboxPurchase(null))
                .isInstanceOf(AppException.class);

        CoinBundle inactive = CoinBundle.builder().id(UUID.randomUUID()).isActive(false).build();
        assertThatThrownBy(() -> coinBundleValidator.validateForSandboxPurchase(inactive))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validateForUpdate: ném lỗi khi mã bundle mới bị trùng với bundle khác")
    void validateForUpdate_DuplicateCodeThrows() {
        UUID id = UUID.randomUUID();
        CoinBundle bundle = CoinBundle.builder().id(id).code("OLD_CODE").build();
        com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest request = com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest.builder()
                .code("NEW_CODE")
                .name("Updated")
                .build();
        when(coinBundleRepository.existsByCodeAndIdNot("NEW_CODE", id)).thenReturn(true);

        assertThatThrownBy(() -> coinBundleValidator.validateForUpdate(id, bundle, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("validateForUpdate: ném lỗi khi gói nạp đang ở trạng thái Đang Bán (ACTIVE)")
    void validateForUpdate_ActiveThrows() {
        UUID id = UUID.randomUUID();
        CoinBundle activeBundle = CoinBundle.builder().id(id).code("BUNDLE_50K").isActive(true).build();
        UpdateCoinBundleRequest request = UpdateCoinBundleRequest.builder().name("Updated").build();

        assertThatThrownBy(() -> coinBundleValidator.validateForUpdate(id, activeBundle, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Tắt Bán");
    }

    @Test
    @DisplayName("validateForDelete: ném lỗi khi gói nạp đang ở trạng thái Đang Bán (ACTIVE)")
    void validateForDelete_ActiveThrows() {
        UUID id = UUID.randomUUID();
        CoinBundle activeBundle = CoinBundle.builder().id(id).code("BUNDLE_50K").isActive(true).build();

        assertThatThrownBy(() -> coinBundleValidator.validateForDelete(id, activeBundle))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Tắt Bán");
    }

    @Test
    @DisplayName("validateForDelete: ném lỗi khi gói nạp đã từng có lịch sử giao dịch")
    void validateForDelete_HasOrdersThrows() {
        UUID id = UUID.randomUUID();
        CoinBundle inactiveBundle = CoinBundle.builder().id(id).code("BUNDLE_50K").isActive(false).build();
        when(orderRepository.existsByBundleId(id)).thenReturn(true);

        assertThatThrownBy(() -> coinBundleValidator.validateForDelete(id, inactiveBundle))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("lịch sử giao dịch");
    }

    @Test
    @DisplayName("validateForDelete: thành công khi gói nạp đã Tắt Bán và chưa có giao dịch")
    void validateForDelete_Success() {
        UUID id = UUID.randomUUID();
        CoinBundle inactiveBundle = CoinBundle.builder().id(id).code("BUNDLE_50K").isActive(false).build();
        when(orderRepository.existsByBundleId(id)).thenReturn(false);

        assertThatCode(() -> coinBundleValidator.validateForDelete(id, inactiveBundle))
                .doesNotThrowAnyException();
    }
}
