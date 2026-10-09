package com.msspoker.authservice.dto.profile.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Minimal projection for later profile APIs; never serialize persistence entities.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProfileResponse {
    private UUID accountId;
    private String displayName;
    private String avatarUrl;
}
