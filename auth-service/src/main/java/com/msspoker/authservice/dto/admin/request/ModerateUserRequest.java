package com.msspoker.authservice.dto.admin.request;

import com.msspoker.authservice.enums.AccountModerationAction;
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
public class ModerateUserRequest {
    @NotNull
    private AccountModerationAction action;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
