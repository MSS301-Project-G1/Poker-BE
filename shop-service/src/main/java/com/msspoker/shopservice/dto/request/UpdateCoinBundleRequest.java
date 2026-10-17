package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateCoinBundleRequest(
        @NotBlank(message = "Tên gói nạp không được để trống")
        @Size(max = 100, message = "Tên gói nạp tối đa 100 ký tự")
        String name,

        @NotBlank(message = "Tên code không được để trống ")
        @Size(max = 100, message = "Tên gói nạp tối đa 100 ký tự")
        String code,

        @Positive(message = "Giá tiền VND phải lớn hơn 0")
        long priceVnd,

        @Positive(message = "Số VIP Coin gốc phải lớn hơn 0")
        long baseVipCoin,

        @Min(value = 0, message = "Số VIP Coin thưởng không được âm")
        long bonusVipCoin,

        @Min(value = 0, message = "Phần trăm thưởng không được âm")
        int bonusPercentage,

        @Size(max = 50, message = "Nhãn huy hiệu tối đa 50 ký tự")
        String badgeLabel,

        @Size(max = 500, message = "Đường dẫn icon tối đa 500 ký tự")
        String iconUrl,

        @Min(value = 0, message = "Thứ tự sắp xếp không được âm")
        int sortOrder,

        boolean isActive
) {}
