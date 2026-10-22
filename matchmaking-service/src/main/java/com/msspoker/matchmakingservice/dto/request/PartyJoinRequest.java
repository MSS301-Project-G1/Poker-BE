package com.msspoker.matchmakingservice.dto.request;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PartyJoinRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z0-9]{8}", message = "Mã party phải gồm 8 ký tự chữ hoa hoặc số")
        String partyCode,
        @NotBlank
        @Pattern(regexp = "\\d{" +MatchmakingConstants.PARTY_PASSWORD_DIGITS + "}",
                message = "Mật khẩu party phải gồm " + MatchmakingConstants.PARTY_PASSWORD_DIGITS + " chữ số")
        String password
) {
}
