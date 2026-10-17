package com.msspoker.matchmakingservice.service;

import com.msspoker.matchmakingservice.model.PartySnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PartyStore {
    CreateResult create(UUID partyId, String partyCode, UUID leaderId, Instant createdAt);
    Optional<PartySnapshot> findByAccountId(UUID accountId);
    Optional<PartySnapshot> findByCode(String partyCode);
    InviteResult invite(UUID leaderId, UUID inviteeId, Duration ttl);
    JoinResult join(UUID accountId, String partyCode, int maxMembers, Instant joinedAt);
    KickResult kick(UUID leaderId, UUID accountId);
    LeaveResult leave(UUID accountId);

    enum CreateResult { CREATED, ALREADY_IN_PARTY, CODE_CONFLICT }
    enum InviteResult { INVITED, PARTY_NOT_FOUND, INVITEE_ALREADY_IN_PARTY, NOT_LEADER }
    enum JoinResult { JOINED, PARTY_NOT_FOUND, ALREADY_IN_PARTY, INVITE_REQUIRED, PARTY_FULL, PARTY_QUEUED }
    enum KickResult { KICKED, PARTY_NOT_FOUND, NOT_LEADER, TARGET_NOT_MEMBER, CANNOT_KICK_LEADER, PARTY_QUEUED }
    enum LeaveResult { LEFT, DISBANDED, NOT_MEMBER, PARTY_QUEUED }
}
