package com.msspoker.shopservice.mapper;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.dto.response.CoinBundleResponse;
import com.msspoker.shopservice.entity.CoinBundle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CoinBundleMapper {

    @Mapping(target = "totalVipCoin", expression = "java(bundle.getTotalVipCoin())")
    @Mapping(target = "isActive", expression = "java(bundle.isActive())")
    CoinBundleResponse toCoinBundleResponse(CoinBundle bundle);

    List<CoinBundleResponse> toCoinBundleResponseList(List<CoinBundle> bundles);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CoinBundle toCoinBundle(CreateCoinBundleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", source = "isActive")
    void updateCoinBundleFromDto(UpdateCoinBundleRequest request, @MappingTarget CoinBundle bundle);
}
