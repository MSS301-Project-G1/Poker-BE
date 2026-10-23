package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.client.WalletClient;
import com.msspoker.shopservice.dto.response.SandboxPurchaseResponse;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.mapper.SandboxPaymentMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SandboxPaymentServiceImplTest {

    @Mock
    private CoinBundleRepository coinBundleRepository;

    @Mock
    private WalletClient walletClient;

    @Spy
    private SandboxPaymentMapper sandboxPaymentMapper = Mappers.getMapper(SandboxPaymentMapper.class);

    private SandboxPaymentServiceImpl sandboxPaymentService;

    private UUID bundleId;
    private UUID accountId;
    private CoinBundle bundle;

    @BeforeEach
    void setUp() {
        sandboxPaymentService = new SandboxPaymentServiceImpl(
                coinBundleRepository,
                walletClient,
                sandboxPaymentMapper,
                new com.msspoker.shopservice.validator.impl.CoinBundleValidatorImpl(coinBundleRepository)
        );

        bundleId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        bundle = CoinBundle.builder()
                .id(bundleId)
                .code("BUNDLE_100K")
                .name("Gói Nạp Khởi Nghiệp")
                .priceVnd(100_000L)
                .baseVipCoin(100L)
                .bonusVipCoin(20L)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("purchaseSandboxBundle thành công: cộng VIP Coin vào ví qua WalletClient")
    void purchaseSandboxBundleSuccess() {
        when(coinBundleRepository.findById(bundleId)).thenReturn(Optional.of(bundle));

        SandboxPurchaseResponse response = sandboxPaymentService.purchaseSandboxBundle(accountId, bundleId);

        assertThat(response).isNotNull();
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.bundleCode()).isEqualTo("BUNDLE_100K");
        assertThat(response.vipCoinCredited()).isEqualTo(120L); // 100 base + 20 bonus

        verify(walletClient).creditVipCoin(
                eq(accountId),
                eq(120L),
                eq("TOPUP_BUNDLE_DEV_MOCK"),
                eq("BUNDLE_100K"),
                anyString()
        );
    }

    @Test
    @DisplayName("purchaseSandboxBundle thất bại khi không tìm thấy gói nạp")
    void purchaseSandboxBundleNotFoundThrows() {
        when(coinBundleRepository.findById(bundleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sandboxPaymentService.purchaseSandboxBundle(accountId, bundleId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Gói nạp VIP Coin không tồn tại");
    }
}
