package com.msspoker.matchmakingservice.repository;

import com.msspoker.matchmakingservice.entity.PartyInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyInvitationRepository extends JpaRepository<PartyInvitationEntity, UUID> {
    @Modifying
    @Query(value = "INSERT INTO party_invitation_locks(party_id) VALUES (:id) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensurePartyLock(UUID id);

    @Query(value = "SELECT party_id FROM party_invitation_locks WHERE party_id=:id FOR UPDATE", nativeQuery = true)
    UUID lockParty(UUID id);

    @Query(value = "SELECT party_id FROM party_invitations WHERE id=:id", nativeQuery = true)
    Optional<UUID> partyId(UUID id);

    Optional<PartyInvitationEntity> findByPartyIdAndRecipientIdAndStatus(UUID party, UUID recipient, String status);
    List<PartyInvitationEntity> findTop50ByRecipientIdOrderByCreatedAtDesc(UUID actor);
    List<PartyInvitationEntity> findTop50ByInviterIdOrderByCreatedAtDesc(UUID actor);
    long countByInviterIdAndCreatedAtAfter(UUID actor, Instant after);
}
