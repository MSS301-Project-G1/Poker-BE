package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.response.PartyResponse;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.service.PartyService;
import com.msspoker.matchmakingservice.service.PartyStore;
import com.msspoker.matchmakingservice.service.QueueStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PartyServiceImpl implements PartyService {
    private static final int CODE_ATTEMPTS = 5;

    private final PartyStore store;
    private final MatchmakingProperties properties;
    private final Clock clock;
    private final QueueStore queueStore;

    public PartyServiceImpl(
            PartyStore store,
            MatchmakingProperties properties,
            Clock clock,
            QueueStore queueStore
    ) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        this.queueStore = queueStore;
    }

    @Override
    public java.util.Optional<PartyResponse> current(UUID accountId) {
        return store.findByAccountId(accountId).map(PartyResponse::from);
    }

    @Override
    public PartyResponse create(UUID accountId) {
        ensureNotQueued(accountId);
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            UUID partyId = UUID.randomUUID();
            String partyCode = code();
            Instant createdAt = clock.instant();
            PartyStore.CreateResult result = store.create(partyId, partyCode, accountId, createdAt);
            if (result == PartyStore.CreateResult.CREATED) {
                return PartyResponse.from(new PartySnapshot(
                        partyId, partyCode, accountId, java.util.List.of(accountId), createdAt
                ));
            }
            if (result == PartyStore.CreateResult.ALREADY_IN_PARTY) {
                throw error(MatchmakingError.MM_ALREADY_IN_PARTY, HttpStatus.CONFLICT,
                        "Tài khoản đã là thành viên của một party.");
            }
        }
        throw error(MatchmakingError.MM_INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể tạo mã party duy nhất. Vui lòng thử lại.");
    }

    @Override
    public void invite(UUID accountId, UUID inviteeId) {
        if (accountId.equals(inviteeId)) {
            throw error(MatchmakingError.MM_INVALID_REQUEST, HttpStatus.BAD_REQUEST,
                    "Không thể tự mời chính mình vào party.");
        }
        store.findByAccountId(accountId).ifPresent(this::ensurePartyMutable);
        ensureNotQueued(inviteeId);
        PartyStore.InviteResult result = store.invite(accountId, inviteeId, properties.getParty().getInviteTtl());
        switch (result) {
            case INVITED -> { return; }
            case PARTY_NOT_FOUND -> throw partyNotFound();
            case INVITEE_ALREADY_IN_PARTY -> throw error(MatchmakingError.MM_ALREADY_IN_PARTY,
                    HttpStatus.CONFLICT, "Người được mời đã là thành viên của một party.");
            case NOT_LEADER -> throw notLeader();
        }
    }

    @Override
    public PartyResponse join(UUID accountId, String partyCode) {
        String normalizedCode = partyCode.toUpperCase(Locale.ROOT);
        ensureNotQueued(accountId);
        PartySnapshot targetParty = store.findByCode(normalizedCode).orElseThrow(PartyServiceImpl::partyNotFound);
        ensurePartyMutable(targetParty);
        PartyStore.JoinResult result = store.join(
                accountId,
                normalizedCode,
                properties.getParty().getMaxMembers(),
                clock.instant()
        );
        switch (result) {
            case PARTY_NOT_FOUND -> throw partyNotFound();
            case ALREADY_IN_PARTY -> throw error(MatchmakingError.MM_ALREADY_IN_PARTY,
                    HttpStatus.CONFLICT, "Tài khoản đã là thành viên của một party.");
            case INVITE_REQUIRED -> throw error(MatchmakingError.MM_PARTY_INVITE_REQUIRED,
                    HttpStatus.FORBIDDEN, "Tài khoản chưa được chủ party mời.");
            case PARTY_FULL -> throw error(MatchmakingError.MM_PARTY_FULL,
                    HttpStatus.CONFLICT, "Party đã đủ thành viên.");
            case PARTY_QUEUED -> throw partyQueued();
            case JOINED -> { }
        }
        return store.findByAccountId(accountId)
                .map(PartyResponse::from)
                .orElseThrow(() -> error(MatchmakingError.MM_INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR, "Không đọc được party vừa tham gia."));
    }

    @Override
    public void kick(UUID accountId, UUID targetAccountId) {
        PartySnapshot currentParty = store.findByAccountId(accountId).orElseThrow(PartyServiceImpl::partyNotFound);
        ensurePartyMutable(currentParty);
        PartyStore.KickResult result = store.kick(accountId, targetAccountId);
        switch (result) {
            case KICKED -> { return; }
            case PARTY_NOT_FOUND -> throw partyNotFound();
            case NOT_LEADER, CANNOT_KICK_LEADER -> throw notLeader();
            case TARGET_NOT_MEMBER -> throw error(MatchmakingError.MM_NOT_PARTY_MEMBER,
                    HttpStatus.NOT_FOUND, "Người chơi không thuộc party này.");
            case PARTY_QUEUED -> throw partyQueued();
        }
    }

    @Override
    public void leave(UUID accountId) {
        PartySnapshot currentParty = store.findByAccountId(accountId)
                .orElseThrow(() -> error(MatchmakingError.MM_NOT_PARTY_MEMBER, HttpStatus.NOT_FOUND,
                        "Tài khoản không thuộc party nào."));
        ensurePartyMutable(currentParty);
        PartyStore.LeaveResult result = store.leave(accountId);
        if (result == PartyStore.LeaveResult.NOT_MEMBER) {
            throw error(MatchmakingError.MM_NOT_PARTY_MEMBER, HttpStatus.NOT_FOUND,
                    "Tài khoản không thuộc party nào.");
        }
        if (result == PartyStore.LeaveResult.PARTY_QUEUED) throw partyQueued();
    }

    private void ensurePartyMutable(PartySnapshot party) {
        if (party.memberIds().stream().anyMatch(memberId -> queueStore.find(memberId).isPresent())) {
            throw error(MatchmakingError.MM_MATCH_IN_PROGRESS, HttpStatus.CONFLICT,
                    "Party đang tìm trận hoặc đang được tạo bàn, không thể thay đổi thành viên.");
        }
    }

    private void ensureNotQueued(UUID accountId) {
        if (queueStore.find(accountId).isPresent()) {
            throw error(MatchmakingError.MM_MATCH_IN_PROGRESS, HttpStatus.CONFLICT,
                    "Tài khoản đang tìm trận, không thể thay đổi party.");
        }
    }

    private static String code() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static MatchmakingApiException partyNotFound() {
        return error(MatchmakingError.MM_PARTY_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy party hoặc mã party không hợp lệ.");
    }

    private static MatchmakingApiException notLeader() {
        return error(MatchmakingError.MM_NOT_PARTY_LEADER, HttpStatus.FORBIDDEN,
                "Chỉ chủ party được thực hiện thao tác này.");
    }

    private static MatchmakingApiException partyQueued() {
        return error(MatchmakingError.MM_MATCH_IN_PROGRESS, HttpStatus.CONFLICT,
                "Party đang tìm trận hoặc đang được tạo bàn, không thể thay đổi thành viên.");
    }

    private static MatchmakingApiException error(MatchmakingError code, HttpStatus status, String message) {
        return new MatchmakingApiException(code, status, message);
    }
}
