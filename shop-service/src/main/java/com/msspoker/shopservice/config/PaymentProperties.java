package com.msspoker.shopservice.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cấu hình tập trung toàn bộ các URL, endpoint và tham số thanh toán.
 * Tách biệt cấu hình ra khỏi Service để các Service (OrderService, v.v.) tái sử dụng dễ dàng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Component
public class PaymentProperties {

    @Value("${payos.return-url:http://localhost:5173/shop/payment/success}")
    private String returnUrl;

    @Value("${payos.cancel-url:http://localhost:5173/shop/payment/cancel}")
    private String cancelUrl;

    @Value("${shop.order.expire-minutes:15}")
    private int expireMinutes;

    @Value("${vietqr.template-url:https://img.vietqr.io/image}")
    private String vietqrTemplateUrl;
}
