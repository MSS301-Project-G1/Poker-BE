package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.dto.response.CoinBundleResponse;
import com.msspoker.shopservice.entity.CoinBundle;
import com.msspoker.shopservice.mapper.CoinBundleMapper;
import com.msspoker.shopservice.repository.CoinBundleRepository;
import com.msspoker.shopservice.service.CoinBundleService;
import com.msspoker.shopservice.validator.CoinBundleValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoinBundleServiceImpl implements CoinBundleService {

    private final CoinBundleRepository coinBundleRepository;
    private final CoinBundleMapper coinBundleMapper;
    private final CoinBundleValidator coinBundleValidator;

    @Override
    @Cacheable(value = "coin_bundles", key = "'active_bundles'")
    public List<CoinBundleResponse> getActiveBundles() {
        log.debug("Fetching active coin bundles from DB");
        List<CoinBundle> bundles = coinBundleRepository.findByIsActiveTrueOrderBySortOrderAsc();
        return coinBundleMapper.toCoinBundleResponseList(bundles);
    }

    @Override
    public CoinBundle getBundleEntity(UUID id) {
        log.debug("Fetching coin bundle entity by id={}", id);
        CoinBundle bundle = coinBundleRepository.findByIdAndIsActiveTrue(id).orElse(null);
        coinBundleValidator.validateExists(id, bundle);
        return bundle;
    }

    @Override
    public List<CoinBundleResponse> getAllBundles() {
        log.debug("Fetching all coin bundles (admin)");
        List<CoinBundle> bundles = coinBundleRepository.findAllByOrderBySortOrderAsc();
        return coinBundleMapper.toCoinBundleResponseList(bundles);
    }

    @Override
    public CoinBundleResponse getBundleDetail(UUID id) {
        log.debug("Fetching bundle detail for id={}", id);
        CoinBundle bundle = coinBundleRepository.findById(id).orElse(null);
        coinBundleValidator.validateExists(id, bundle);
        return coinBundleMapper.toCoinBundleResponse(bundle);
    }

    @Override
    @Transactional
    @CacheEvict(value = "coin_bundles", allEntries = true)
    public CoinBundleResponse createBundle(CreateCoinBundleRequest request) {
        log.info("Creating new coin bundle with code={}", request.code());
        coinBundleValidator.validateForCreate(request);

        CoinBundle bundle = coinBundleMapper.toCoinBundle(request);
        bundle.setActive(request.isActiveVal());
        if (request.bonusPercentage() == null) {
            bundle.setBonusPercentage(request.bonusPercentageVal());
        }
        if (request.sortOrder() == null) {
            bundle.setSortOrder(request.sortOrderVal());
        }
        CoinBundle saved = coinBundleRepository.save(bundle);
        log.info("Created coin bundle id={}, code={} successfully", saved.getId(), saved.getCode());
        return coinBundleMapper.toCoinBundleResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "coin_bundles", allEntries = true)
    public CoinBundleResponse updateBundle(UUID id, UpdateCoinBundleRequest request) {
        log.info("Updating coin bundle id={}", id);
        CoinBundle bundle = coinBundleRepository.findById(id).orElse(null);
        coinBundleValidator.validateForUpdate(id, bundle, request);

        String existingCode = bundle.getCode();
        coinBundleMapper.updateCoinBundleFromDto(request, bundle);
        if (request.code() == null || request.code().isBlank()) {
            bundle.setCode(existingCode);
        } else {
            bundle.setCode(request.code().trim().toUpperCase());
        }
        if (request.isActive() != null) {
            bundle.setActive(request.isActive());
        }
        if (request.bonusPercentage() == null) {
            bundle.setBonusPercentage(request.bonusPercentageVal());
        }
        CoinBundle updated = coinBundleRepository.save(bundle);
        log.info("Updated coin bundle id={} successfully", id);
        return coinBundleMapper.toCoinBundleResponse(updated);
    }

    @Override
    @Transactional
    @CacheEvict(value = "coin_bundles", allEntries = true)
    public void deleteBundle(UUID id) {
        log.info("Deleting coin bundle id={}", id);
        CoinBundle bundle = coinBundleRepository.findById(id).orElse(null);
        coinBundleValidator.validateExists(id, bundle);
        coinBundleValidator.validateForDelete(id, bundle);

        coinBundleRepository.delete(bundle);
        log.info("Deleted coin bundle id={} successfully", id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "coin_bundles", allEntries = true)
    public CoinBundleResponse toggleActive(UUID id) {
        return toggleActive(id, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = "coin_bundles", allEntries = true)
    public CoinBundleResponse toggleActive(UUID id, Boolean isActive) {
        log.info("Toggling active status for coin bundle id={}, explicitActive={}", id, isActive);
        CoinBundle bundle = coinBundleRepository.findById(id).orElse(null);
        coinBundleValidator.validateExists(id, bundle);

        boolean targetStatus = (isActive != null) ? isActive : !bundle.isActive();
        bundle.setActive(targetStatus);
        CoinBundle updated = coinBundleRepository.save(bundle);
        log.info("Toggled active status for bundle id={} to {}", id, updated.isActive());
        return coinBundleMapper.toCoinBundleResponse(updated);
    }
}
