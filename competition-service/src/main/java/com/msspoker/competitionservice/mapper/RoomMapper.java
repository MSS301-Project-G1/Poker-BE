package com.msspoker.competitionservice.mapper;

import com.msspoker.competitionservice.dto.request.CreateRoomRequest;
import com.msspoker.competitionservice.dto.request.TableSettingsRequest;
import com.msspoker.competitionservice.dto.response.RoomMemberResponse;
import com.msspoker.competitionservice.dto.response.RoomResponse;
import com.msspoker.competitionservice.dto.response.RoomSummaryResponse;
import com.msspoker.competitionservice.entity.CustomRoomEntity;
import com.msspoker.competitionservice.entity.RoomMemberEntity;
import com.msspoker.competitionservice.enums.RoomStatus;
import com.msspoker.competitionservice.util.CompetitionIds;

import org.mapstruct.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {CompetitionIds.class, RoomStatus.class})
public interface RoomMapper {
    @Mapping(target = "id", expression = "java(CompetitionIds.next())")
    @Mapping(target = "status", expression = "java(RoomStatus.WAITING)")
    @Mapping(target = "currentSourceId", ignore = true)
    @Mapping(target = "tableId", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    CustomRoomEntity newRoom(CreateRoomRequest request, String roomCode, UUID ownerId, String passwordHash,
                             Instant createdAt);

    @Mapping(target = "id", expression = "java(CompetitionIds.next())")
    @Mapping(target = "ready", ignore = true)
    RoomMemberEntity newMember(UUID roomId, UUID accountId, Instant joinedAt);

    RoomMemberResponse member(RoomMemberEntity member, boolean owner);

    @Mapping(target = "hasPassword", expression = "java(room.getPasswordHash() != null)")
    RoomResponse room(CustomRoomEntity room, List<RoomMemberResponse> members);

    @Mapping(target = "hasPassword", expression = "java(room.getPasswordHash() != null)")
    RoomSummaryResponse summary(CustomRoomEntity room, int playerCount);

    TableSettingsRequest settings(CustomRoomEntity room, long startingChips, int turnTimeSeconds);
}
