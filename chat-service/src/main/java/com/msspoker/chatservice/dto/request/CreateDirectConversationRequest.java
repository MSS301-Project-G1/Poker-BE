package com.msspoker.chatservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDirectConversationRequest(@NotNull UUID accountId) {}
