package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.dto.response.CoinBundleResponse;
import com.msspoker.shopservice.entity.CoinBundle;

import java.util.List;
import java.util.UUID;

public interface CoinBundleService {

    List<CoinBundleResponse> getActiveBundles();

    CoinBundle getBundleEntity(UUID id);

    List<CoinBundleResponse> getAllBundles();

    CoinBundleResponse getBundleDetail(UUID id);

    CoinBundleResponse createBundle(CreateCoinBundleRequest request);

    CoinBundleResponse updateBundle(UUID id, UpdateCoinBundleRequest request);

    void deleteBundle(UUID id);

    CoinBundleResponse toggleActive(UUID id);
}
