package com.msspoker.competitionservice.dto.response;

/** Error body returned by game-service and wallet-service; only the code is used for mapping. */
public record ServiceErrorResponse(String code, String message) {
}
