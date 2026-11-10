package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.exception.OrderExceptions;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.repository.OrderRepository;
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
    private final OrderRepository orderRepository;

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
        if (bundle.isActive()) {
            throw AppException.of(HttpStatus.BAD_REQUEST, "BUNDLE_MUST_BE_INACTIVE_BEFORE_UPDATE",
                    "Gói nạp đang ở trạng thái Đang Bán. Vui lòng Tắt Bán gói nạp trước khi chỉnh sửa thông tin!");
        }
        if (request != null && request.code() != null && !request.code().isBlank()) {
            String normalizedCode = request.code().trim().toUpperCase();
            if (coinBundleRepository.existsByCodeAndIdNot(normalizedCode, id)) {
                throw AppException.of(HttpStatus.CONFLICT, "BUNDLE_CODE_EXISTS", "Mã gói nạp '" + normalizedCode + "' đã tồn tại");
            }
        }
    }

    @Override
    public void validateForDelete(UUID id, CoinBundle bundle) {
        validateExists(id, bundle);
        if (bundle.isActive()) {
            throw AppException.of(HttpStatus.BAD_REQUEST, "BUNDLE_MUST_BE_INACTIVE_BEFORE_DELETE",
                    "Gói nạp đang ở trạng thái Đang Bán. Vui lòng Tắt Bán gói nạp trước khi xóa khỏi hệ thống!");
        }
        if (orderRepository.existsByBundleId(id)) {
            throw AppException.of(HttpStatus.CONFLICT, "BUNDLE_HAS_PURCHASE_HISTORY",
                    "Không thể xóa gói nạp đã từng có lịch sử giao dịch. Bạn có thể Tắt Bán để ngừng phát hành gói này.");
        }
    }

    @Override
    public void validateForSandboxPurchase(CoinBundle bundle) {
        if (bundle == null || !bundle.isActive()) {
            throw OrderExceptions.bundleNotFound();
        }
    }
}
