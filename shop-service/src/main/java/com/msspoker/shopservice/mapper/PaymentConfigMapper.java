package com.msspoker.shopservice.mapper;

import com.msspoker.shopservice.dto.request.CreatePaymentConfigRequest;
import com.msspoker.shopservice.dto.request.UpdatePaymentConfigRequest;
import com.msspoker.shopservice.dto.response.PaymentConfigResponse;
import com.msspoker.shopservice.entity.PaymentConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentConfigMapper {

    @Mapping(target = "isActive", expression = "java(config.isActive())")
    @Mapping(target = "isMain", expression = "java(config.isMain())")
    PaymentConfigResponse toPaymentConfigResponse(PaymentConfig config);

    List<PaymentConfigResponse> toPaymentConfigResponseList(List<PaymentConfig> configs);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "false")
    @Mapping(target = "main", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PaymentConfig toPaymentConfig(CreatePaymentConfigRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "main", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updatePaymentConfigFromRequest(UpdatePaymentConfigRequest request, @MappingTarget PaymentConfig config);
}
