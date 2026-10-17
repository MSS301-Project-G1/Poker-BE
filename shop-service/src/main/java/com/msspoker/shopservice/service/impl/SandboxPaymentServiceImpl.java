package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.client.WalletClient;
import com.msspoker.shopservice.dto.response.SandboxPurchaseResponse;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.mapper.SandboxPaymentMapper;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.service.SandboxPaymentService;
import com.msspoker.shopservice.validator.CoinBundleValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!prod")
@ConditionalOnProperty(name = "features.sandbox-payment.enabled", havingValue = "true", matchIfMissing = true)
public class SandboxPaymentServiceImpl implements SandboxPaymentService {

    private final CoinBundleRepository coinBundleRepository;
    private final WalletClient walletClient;
    private final SandboxPaymentMapper sandboxPaymentMapper;
    private final CoinBundleValidator coinBundleValidator;

    @Override
    @Transactional
    public SandboxPurchaseResponse purchaseSandboxBundle(UUID accountId, UUID bundleId) {
        log.info("Xử lý nạp Sandbox bundle: accountId={}, bundleId={}", accountId, bundleId);

        CoinBundle bundle = coinBundleRepository.findById(bundleId).orElse(null);

        // Kiểm tra hợp lệ qua CoinBundleValidator
        coinBundleValidator.validateForSandboxPurchase(bundle);

        long vipCoinAmount = bundle.getVipCoinAmount();
        String idempotencyKey = "SANDBOX_TOPUP:" + accountId + ":" + bundleId + ":" + System.nanoTime();

        walletClient.creditVipCoin(
                accountId,
                vipCoinAmount,
                "TOPUP_BUNDLE_DEV_MOCK",
                bundle.getCode(),
                idempotencyKey
        );

        log.info("Sandbox credit thành công: accountId={}, bundle={}, vipCoinCredited={}",
                accountId, bundle.getCode(), vipCoinAmount);

        String message = "Sandbox: Đã cộng " + vipCoinAmount + " VIP Coin vào ví tài khoản thành công.";
        return sandboxPaymentMapper.toSandboxPurchaseResponse(accountId, bundle, vipCoinAmount, message);
    }
}
