package com.msspoker.shopservice.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái vận hành của sản phẩm trong hệ thống Shop")
public enum ProductStatus {
    @Schema(description = "Sản phẩm đang mở bán bình thường trên Cửa Hàng")
    ACTIVE,

    @Schema(description = "Sản phẩm đang tạm ngưng / thu hồi khỏi Cửa Hàng để quản trị viên bảo trì, sửa lỗi")
    MAINTAIN
}

