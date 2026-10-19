package com.msspoker.authservice.dto.profile.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateAvatarPresetRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Boolean active;
}
