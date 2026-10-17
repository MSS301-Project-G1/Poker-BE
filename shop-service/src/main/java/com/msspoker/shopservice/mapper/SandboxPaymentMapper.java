package com.msspoker.shopservice.mapper;

import com.msspoker.shopservice.dto.response.SandboxPurchaseResponse;
import com.msspoker.shopservice.entity.CoinBundle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface SandboxPaymentMapper {

    @Mapping(target = "bundleCode", source = "code")
    @Mapping(target = "bundleName", source = "name")
    @Mapping(target = "vipCoinCredited", source = "vipCoinAmount")
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "message", ignore = true)
    SandboxPurchaseResponse toSandboxPurchaseResponse(CoinBundle bundle);

    default SandboxPurchaseResponse toSandboxPurchaseResponse(UUID accountId, CoinBundle bundle, long vipCoinAmount, String message) {
        SandboxPurchaseResponse response = toSandboxPurchaseResponse(bundle);
        if (response == null) {
            return null;
        }
        return new SandboxPurchaseResponse(
                accountId,
                response.bundleCode(),
                response.bundleName(),
                vipCoinAmount,
                message
        );
    }
}
