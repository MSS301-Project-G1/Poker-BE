package com.msspoker.shopservice.security;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation dùng để inject UUID của người dùng hiện tại (lấy từ Security Context do Gateway cung cấp).
 * Ẩn hoàn toàn khỏi Swagger parameter để tester không phải nhập thủ công.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(hidden = true)
public @interface CurrentUser {

    /**
     * Nếu true và không tìm thấy user thì ném 401 Unauthorized.
     * Nếu false (dùng cho các API public cần enrich thông tin như /api/shop/products), trả về null.
     */
    boolean required() default true;
}
