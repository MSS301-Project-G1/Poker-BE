package com.msspoker.chatservice.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record SendMessageRequest(
    @NotNull @Pattern(regexp = "TEXT") String messageType,
    @NotBlank String content,
    UUID clientMessageId) {}
