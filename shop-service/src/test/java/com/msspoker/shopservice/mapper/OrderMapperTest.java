package com.msspoker.shopservice.mapper;

import com.msspoker.shopservice.entity.Order;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {
    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

    @Test
    void payosQrPayloadIsPreservedWithoutInventingBankDetails() {
        var order = Order.builder().payosOrderCode(123456L).amountVnd(50000L)
                .checkoutUrl("https://pay.payos.vn/web/fixture").qrCode("000201010212-provider-payload").build();
        var response = mapper.toOrderResponse(order);
        assertThat(response.qrCode()).isEqualTo(order.getQrCode());
        assertThat(response.qrImageUrl()).isNull();
    }

    @Test
    void manualBankQrKeepsTheImageSnapshotCreatedForTheOrder() {
        String image = "https://img.vietqr.io/image/TEST-123-compact2.png?amount=50000&addInfo=MSS%20VIP%20123456";
        var order = Order.builder().payosOrderCode(123456L).checkoutUrl(image).qrCode(image).build();
        assertThat(mapper.toOrderResponse(order).qrImageUrl()).isEqualTo(image);
    }
}
