package com.msspoker.shopservice.mapper;

import com.msspoker.shopservice.dto.response.OrderResponse;
import com.msspoker.shopservice.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "bundleId", source = "bundle.id")
    @Mapping(target = "bundleName", source = "bundle.name")
    @Mapping(target = "vipCoinAmount", source = "vipCoinAmount")
    @Mapping(target = "qrImageUrl", expression = "java(generateQrImageUrl(order))")
    OrderResponse toOrderResponse(Order order);

    List<OrderResponse> toOrderResponseList(List<Order> orders);

    default String generateQrImageUrl(Order order) {
        if (order == null || order.getQrCode() == null) {
            return null;
        }
        // Manual-bank orders store the configured VietQR image URL when the order is created.
        // PayOS orders store an EMV QR payload; FE renders that exact payload locally.
        String qrCode = order.getQrCode();
        return (qrCode.startsWith("https://") || qrCode.startsWith("http://"))
                && qrCode.equals(order.getCheckoutUrl()) ? qrCode : null;
    }
}
