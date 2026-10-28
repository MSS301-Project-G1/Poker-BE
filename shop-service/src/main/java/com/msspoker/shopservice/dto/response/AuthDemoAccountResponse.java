package com.msspoker.shopservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Body of auth-service GET /internal/demo-accounts. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthDemoAccountResponse(Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(UUID accountId) {
    }
}
