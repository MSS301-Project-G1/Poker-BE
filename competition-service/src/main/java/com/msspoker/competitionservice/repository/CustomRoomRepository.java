package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.CustomRoomEntity;
import com.msspoker.competitionservice.enums.RoomStatus;
import com.msspoker.competitionservice.enums.RoomVisibility;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CustomRoomRepository extends JpaRepository<CustomRoomEntity, UUID> {
    Optional<CustomRoomEntity> findByRoomCodeAndIsDeletedFalse(String roomCode);

    boolean existsByRoomCode(String roomCode);

    Page<CustomRoomEntity> findByVisibilityAndStatusAndIsDeletedFalse(RoomVisibility visibility, RoomStatus status,
                                                                      Pageable pageable);

    /** Serializes membership and state changes of one room. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CustomRoomEntity r where r.roomCode = :roomCode and r.isDeleted = false")
    Optional<CustomRoomEntity> lockByRoomCode(String roomCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CustomRoomEntity r where r.id = :id")
    Optional<CustomRoomEntity> lockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CustomRoomEntity r where r.currentSourceId = :sourceId and r.isDeleted = false")
    Optional<CustomRoomEntity> lockBySourceId(UUID sourceId);

    @Query("""
            select r from CustomRoomEntity r
            where r.isDeleted = false and r.status <> :closed
              and r.id in (select m.roomId from RoomMemberEntity m where m.accountId = :accountId)
            """)
    Optional<CustomRoomEntity> findOpenRoomOf(UUID accountId, RoomStatus closed);
}
