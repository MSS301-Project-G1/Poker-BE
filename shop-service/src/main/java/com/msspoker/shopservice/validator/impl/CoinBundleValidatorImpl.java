package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.exception.OrderExceptions;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.validator.CoinBundleValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementation cho CoinBundleValidator.
 */
@Component
@RequiredArgsConstructor
public class CoinBundleValidatorImpl implements CoinBundleValidator {

    private final CoinBundleRepository coinBundleRepository;

    @Override
    public void validateExists(UUID id, CoinBundle bundle) {
        if (bundle == null) {
            throw OrderExceptions.bundleNotFound();
        }
    }

    @Override
    public void validateForCreate(CreateCoinBundleRequest request) {
        if (request != null && coinBundleRepository.existsByCode(request.code())) {
            throw AppException.of(HttpStatus.CONFLICT, "BUNDLE_CODE_EXISTS", "Mã gói nạp '" + request.code() + "' đã tồn tại");
        }
    }

    @Override
    public void validateForUpdate(UUID id, CoinBundle bundle, UpdateCoinBundleRequest request) {
        validateExists(id, bundle);
        if (request != null && request.code() != null && !request.code().isBlank()) {
            String normalizedCode = request.code().trim().toUpperCase();
            if (coinBundleRepository.existsByCodeAndIdNot(normalizedCode, id)) {
                throw AppException.of(HttpStatus.CONFLICT, "BUNDLE_CODE_EXISTS", "Mã gói nạp '" + normalizedCode + "' đã tồn tại");
            }
        }
    }

    @Override
    public void validateForSandboxPurchase(CoinBundle bundle) {
        if (bundle == null || !bundle.isActive()) {
            throw OrderExceptions.bundleNotFound();
        }
    }
}
