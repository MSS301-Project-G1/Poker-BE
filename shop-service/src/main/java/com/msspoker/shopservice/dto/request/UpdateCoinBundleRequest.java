package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateCoinBundleRequest(
        @NotBlank(message = "Tên gói nạp không được để trống")
        @Size(max = 100, message = "Tên gói nạp tối đa 100 ký tự")
        String name,

        @Size(max = 100, message = "Tên code tối đa 100 ký tự")
        String code,

        @NotNull(message = "Giá tiền VND không được để trống")
        @Positive(message = "Giá tiền VND phải lớn hơn 0")
        Long priceVnd,

        @NotNull(message = "Số VIP Coin gốc không được để trống")
        @Positive(message = "Số VIP Coin gốc phải lớn hơn 0")
        Long baseVipCoin,

        @Min(value = 0, message = "Số VIP Coin thưởng không được âm")
        Long bonusVipCoin,

        @Min(value = 0, message = "Phần trăm thưởng không được âm")
        Integer bonusPercentage,

        @Size(max = 50, message = "Nhãn huy hiệu tối đa 50 ký tự")
        String badgeLabel,

        @Size(max = 500, message = "Đường dẫn icon tối đa 500 ký tự")
        String iconUrl,

        @Min(value = 0, message = "Thứ tự sắp xếp không được âm")
        Integer sortOrder,

        Boolean isActive
) {
    public int bonusPercentageVal() {
        if (bonusPercentage != null) return bonusPercentage;
        if (baseVipCoin != null && baseVipCoin > 0 && bonusVipCoin != null && bonusVipCoin > 0) {
            return Math.round(((float) bonusVipCoin / baseVipCoin) * 100);
        }
        return 0;
    }
}
