package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.TournamentEntity;
import com.msspoker.competitionservice.enums.TournamentStatus;
import com.msspoker.competitionservice.enums.TournamentType;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentRepository extends JpaRepository<TournamentEntity, UUID> {
    Page<TournamentEntity> findByStatusInAndIsDeletedFalse(Collection<TournamentStatus> statuses, Pageable pageable);

    Optional<TournamentEntity> findByIdAndIsDeletedFalse(UUID id);

    /** Tournaments in the status whose start time has come, earliest first. */
    @Query("""
            select t.id from TournamentEntity t where t.status = :status and t.startAt <= :now
            and t.isDeleted = false order by t.startAt, t.id""")
    List<UUID> findDueToStart(TournamentStatus status, Instant now);

    /** Tournaments that have not sent invitations and start before inviteBefore. */
    @Query("""
            select t.id from TournamentEntity t where t.type = :type and t.status = :status
            and t.invitesSentAt is null and t.startAt <= :inviteBefore and t.isDeleted = false
            order by t.startAt, t.id""")
    List<UUID> findDueForInvites(TournamentType type, TournamentStatus status, Instant inviteBefore);

    /** Invited tournaments past their confirmation deadline whose replacements were not sent yet. */
    @Query("""
            select t.id from TournamentEntity t where t.type = :type and t.status = :status
            and t.invitesSentAt is not null and t.replacementsSentAt is null and t.startAt <= :deadlineBefore
            and t.isDeleted = false order by t.startAt, t.id""")
    List<UUID> findDueForReplacements(TournamentType type, TournamentStatus status, Instant deadlineBefore);

    /** Serializes registration, start, cancellation and result handling of one tournament. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TournamentEntity t where t.id = :id and t.isDeleted = false")
    Optional<TournamentEntity> lockById(UUID id);
}
