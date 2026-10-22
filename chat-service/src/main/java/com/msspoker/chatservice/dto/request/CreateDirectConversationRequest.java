package com.msspoker.chatservice.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record CreateDirectConversationRequest(@NotNull UUID accountId) {}
