package com.msspoker.socialservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FriendshipResponse(
      @JsonProperty("isFriend") boolean isFriend, @JsonProperty("isBlocked") boolean isBlocked) {}
