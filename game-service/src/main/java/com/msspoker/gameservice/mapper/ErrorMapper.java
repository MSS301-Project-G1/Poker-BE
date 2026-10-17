package com.msspoker.gameservice.mapper;

import com.msspoker.common.response.ErrorResponse;
import com.msspoker.gameservice.enums.GameError;
import com.msspoker.gameservice.exception.GameApiException;

import org.mapstruct.*;

import java.time.Instant;

@Mapper(componentModel = "spring", imports = Instant.class)
public interface ErrorMapper {
    @Mapping(target = "code", source = "code")
    @Mapping(target = "message", source = "message")
    @Mapping(target = "requestId", source = "requestId")
    @Mapping(target = "timestamp", expression = "java(Instant.now())")
    ErrorResponse error(GameError code, String message, String requestId);

    default ErrorResponse error(GameApiException exception, String requestId) {
        return error(exception.getCode(), exception.getMessage(), requestId);
    }
}
