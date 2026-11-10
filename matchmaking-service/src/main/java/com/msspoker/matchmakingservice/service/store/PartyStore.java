package com.msspoker.matchmakingservice.service.store;

import com.msspoker.matchmakingservice.model.PartySnapshot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PartyStore {
    CreateResult create(UUID partyId, String partyCode, String password, UUID leaderId, Instant createdAt);
    Optional<PartySnapshot> findByAccountId(UUID accountId);
    Optional<PartySnapshot> findByCode(String partyCode);
    JoinResult join(UUID accountId, String partyCode, String password, int maxMembers, Instant joinedAt);
    /** Kick thành công sẽ thay mật khẩu party bằng newPassword để người bị kick không vào lại được. */
    KickResult kick(UUID leaderId, UUID accountId, String newPassword);
    LeaveResult leave(UUID accountId);

    enum CreateResult { CREATED, ALREADY_IN_PARTY, CODE_CONFLICT }
    enum JoinResult { JOINED, PARTY_NOT_FOUND, ALREADY_IN_PARTY, WRONG_PASSWORD, PARTY_FULL, PARTY_QUEUED }
    enum KickResult { KICKED, PARTY_NOT_FOUND, NOT_LEADER, TARGET_NOT_MEMBER, CANNOT_KICK_LEADER, PARTY_QUEUED }
    enum LeaveResult { LEFT, DISBANDED, NOT_MEMBER, PARTY_QUEUED }
}
