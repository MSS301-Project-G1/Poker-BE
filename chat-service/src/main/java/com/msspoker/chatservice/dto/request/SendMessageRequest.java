package com.msspoker.chatservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record SendMessageRequest(
        @NotNull @Pattern(regexp = "TEXT") String messageType,
        @NotBlank String content,
        UUID clientMessageId) {}
