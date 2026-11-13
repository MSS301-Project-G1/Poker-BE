package com.msspoker.socialservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FriendshipResponse(
        @JsonProperty("isFriend") boolean isFriend, @JsonProperty("isBlocked") boolean isBlocked) {}
