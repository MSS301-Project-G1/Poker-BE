package com.msspoker.authservice.dto.profile.response;

import com.msspoker.authservice.enums.GenderEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MyProfileResponse {
    private UUID accountId;
    private String email;
    private String phoneNumber;
    private String displayName;
    private String fullName;
    private String avatarUrl;
    private GenderEnum gender;
    private LocalDate birthDate;
    private String bio;
    private String countryCode;
    private String city;
}
