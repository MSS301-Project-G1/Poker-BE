package com.msspoker.socialservice.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record SendFriendRequest(@NotNull UUID addresseeId) {}
