package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.entity.CoinBundle;

import java.util.UUID;

/**
 * Validator interface tập trung toàn bộ các quy tắc nghiệp vụ cho CoinBundle & Sandbox Purchase.
 */
public interface CoinBundleValidator {

    void validateExists(UUID id, CoinBundle bundle);

    void validateForCreate(CreateCoinBundleRequest request);

    void validateForUpdate(UUID id, CoinBundle bundle, UpdateCoinBundleRequest request);

    void validateForSandboxPurchase(CoinBundle bundle);
}
