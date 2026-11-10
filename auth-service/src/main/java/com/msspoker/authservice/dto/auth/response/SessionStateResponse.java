package com.msspoker.authservice.dto.auth.response;

import java.util.UUID;

public record SessionStateResponse(UUID accountId, UUID sessionId, UUID tabId) {}
