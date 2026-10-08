package com.msspoker.walletservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Cấu trúc phản hồi API chuẩn cho toàn hệ thống")
public class ApiResponse<T> {

    @Schema(description = "Mã trạng thái nghiệp vụ", example = "WALLET_SUCCESS")
    private String code;

    @Schema(description = "Thông điệp mô tả", example = "Thao tác thành công")
    private String message;

    @Schema(description = "Dữ liệu trả về")
    private T data;

    @Schema(description = "Thời gian phản hồi (UTC)")
    private Instant timestamp;

    @Schema(description = "ID định danh request để truy vết log")
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code("WALLET_SUCCESS")
                .message("Thành công")
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code("WALLET_SUCCESS")
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, String requestId) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .requestId(requestId)
                .build();
    }
}
