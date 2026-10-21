package com.msspoker.authservice.dto.profile.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AvatarOptionResponse {
    private UUID avatarId;
    private String name;
    private String avatarUrl;
}
