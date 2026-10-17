package com.msspoker.competitionservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record ReadyRequest(@NotNull Boolean ready) {
}
