package com.msspoker.matchmakingservice.dto.response;

import com.msspoker.matchmakingservice.model.PartySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** password chỉ trả về cho thành viên party để họ chia sẻ cùng mã party. */
public record PartyResponse(
        UUID partyId,
        String partyCode,
        String password,
        UUID leaderId,
        List<UUID> memberIds,
        Instant createdAt
) {
    public static PartyResponse from(PartySnapshot party) {
        return new PartyResponse(
                party.partyId(),
                party.partyCode(),
                party.password(),
                party.leaderId(),
                party.memberIds(),
                party.createdAt()
        );
    }
}
