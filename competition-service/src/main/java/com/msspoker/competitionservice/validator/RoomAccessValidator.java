package com.msspoker.competitionservice.validator;

import com.msspoker.competitionservice.entity.CustomRoomEntity;
import com.msspoker.competitionservice.entity.RoomMemberEntity;
import com.msspoker.competitionservice.enums.RoomStatus;
import com.msspoker.competitionservice.exception.CompetitionExceptions;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RoomAccessValidator {
    public RoomMemberEntity member(List<RoomMemberEntity> members, UUID accountId) {
        return members.stream().filter(m -> m.getAccountId().equals(accountId)).findFirst()
                .orElseThrow(CompetitionExceptions::notMember);
    }

    public void owner(CustomRoomEntity room, UUID accountId) {
        if (!room.getOwnerId().equals(accountId)) throw CompetitionExceptions.ownerOnly();
    }

    /** Membership and ready state are frozen while the room's match is running. */
    public void waiting(CustomRoomEntity room) {
        if (room.getStatus() != RoomStatus.WAITING) throw CompetitionExceptions.roomNotWaiting();
    }
}
