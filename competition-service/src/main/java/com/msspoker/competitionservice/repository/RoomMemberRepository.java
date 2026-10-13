package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.RoomMemberEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomMemberRepository extends JpaRepository<RoomMemberEntity, UUID> {
    /** Join order is also the seat order sent to CreateTable. */
    List<RoomMemberEntity> findByRoomIdOrderByJoinedAtAscIdAsc(UUID roomId);

    long countByRoomId(UUID roomId);
}
