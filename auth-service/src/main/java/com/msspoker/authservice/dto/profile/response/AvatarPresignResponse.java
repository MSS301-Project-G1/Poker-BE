package com.msspoker.authservice.dto.profile.response;

import java.time.Instant;
import java.util.Map;

public record AvatarPresignResponse(String key, String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {
}
