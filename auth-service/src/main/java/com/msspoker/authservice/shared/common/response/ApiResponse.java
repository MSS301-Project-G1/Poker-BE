package com.msspoker.authservice.shared.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    public static final String SUCCESS_CODE = "success_request";

    @Builder.Default
    private String code = SUCCESS_CODE;

    private String message;
    private T result;
    private Instant timestamp;
    private String path;
}
