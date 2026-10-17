package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.dto.response.CoinBundleResponse;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.mapper.CoinBundleMapper;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.validator.CoinBundleValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
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
class CoinBundleServiceImplTest {

    @Mock
    private CoinBundleRepository coinBundleRepository;

    @Spy
    private CoinBundleMapper coinBundleMapper = Mappers.getMapper(CoinBundleMapper.class);

    private CoinBundleServiceImpl coinBundleService;

    private UUID bundleId;
    private CoinBundle bundle;

    @BeforeEach
    void setUp() {
        coinBundleService = new CoinBundleServiceImpl(
                coinBundleRepository,
                coinBundleMapper,
                new CoinBundleValidator(coinBundleRepository)
        );

        bundleId = UUID.randomUUID();
        bundle = CoinBundle.builder()
                .id(bundleId)
                .code("BUNDLE_50K")
                .name("Gói 50k")
                .priceVnd(50_000L)
                .baseVipCoin(50L)
                .bonusVipCoin(5L)
                .bonusPercentage(10)
                .badgeLabel("HOT")
                .isActive(true)
                .sortOrder(1)
                .build();
    }

    @Test
    @DisplayName("getActiveBundles trả về danh sách các gói đang active")
    void getActiveBundlesSuccess() {
        when(coinBundleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(bundle));

        List<CoinBundleResponse> result = coinBundleService.getActiveBundles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("BUNDLE_50K");
        assertThat(result.get(0).totalVipCoin()).isEqualTo(55L);
    }

    @Test
    @DisplayName("getAllBundles trả về tất cả các gói kể cả inactive")
    void getAllBundlesSuccess() {
        CoinBundle inactiveBundle = CoinBundle.builder()
                .id(UUID.randomUUID())
                .code("BUNDLE_OLD")
                .name("Gói Cũ")
                .isActive(false)
                .build();
        when(coinBundleRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(bundle, inactiveBundle));

        List<CoinBundleResponse> result = coinBundleService.getAllBundles();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("createBundle thành công khi mã code chưa tồn tại")
    void createBundleSuccess() {
        CreateCoinBundleRequest request = CreateCoinBundleRequest.builder()
                .code("BUNDLE_200K")
                .name("Gói 200k")
                .priceVnd(200_000L)
                .baseVipCoin(200L)
                .bonusVipCoin(40L)
                .bonusPercentage(20)
                .isActive(true)
                .sortOrder(2)
                .build();

        when(coinBundleRepository.existsByCode("BUNDLE_200K")).thenReturn(false);
        when(coinBundleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CoinBundleResponse response = coinBundleService.createBundle(request);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("BUNDLE_200K");
        verify(coinBundleRepository).save(any());
    }

    @Test
    @DisplayName("createBundle ném Conflict exception khi mã code đã tồn tại")
    void createBundleDuplicateCodeThrows() {
        CreateCoinBundleRequest request = CreateCoinBundleRequest.builder()
                .code("BUNDLE_50K")
                .name("Gói 50k mới")
                .build();

        when(coinBundleRepository.existsByCode("BUNDLE_50K")).thenReturn(true);

        assertThatThrownBy(() -> coinBundleService.createBundle(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("updateBundle thành công cập nhật thông tin gói nạp")
    void updateBundleSuccess() {
        UpdateCoinBundleRequest request = UpdateCoinBundleRequest.builder()
                .name("Gói 50k Siêu Ưu Đãi")
                .priceVnd(45_000L)
                .baseVipCoin(50L)
                .bonusVipCoin(10L)
                .bonusPercentage(20)
                .badgeLabel("SIEU_HOT")
                .sortOrder(1)
                .isActive(true)
                .build();

        when(coinBundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));
        when(coinBundleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CoinBundleResponse response = coinBundleService.updateBundle(bundleId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Gói 50k Siêu Ưu Đãi");
        assertThat(response.priceVnd()).isEqualTo(45_000L);
    }

    @Test
    @DisplayName("toggleActive chuyển trạng thái từ true sang false và ngược lại")
    void toggleActiveSuccess() {
        when(coinBundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));
        when(coinBundleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CoinBundleResponse response = coinBundleService.toggleActive(bundleId);

        assertThat(response.isActive()).isFalse();

        when(coinBundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));
        CoinBundleResponse response2 = coinBundleService.toggleActive(bundleId);

        assertThat(response2.isActive()).isTrue();
    }

    @Test
    @DisplayName("deleteBundle thành công xóa gói nạp khỏi repository")
    void deleteBundleSuccess() {
        when(coinBundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        coinBundleService.deleteBundle(bundleId);

        verify(coinBundleRepository).delete(bundle);
    }
}
