package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.common.event.EventEnvelope;
import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.response.PartyInvitationResponse;
import com.msspoker.matchmakingservice.dto.response.PartyResponse;
import com.msspoker.matchmakingservice.entity.PartyInvitationEntity;
import com.msspoker.matchmakingservice.entity.PartyNotificationEntity;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.repository.PartyInvitationRepository;
import com.msspoker.matchmakingservice.repository.PartyNotificationRepository;
import com.msspoker.matchmakingservice.service.PartyInvitationService;
import com.msspoker.matchmakingservice.service.PartyService;
import com.msspoker.matchmakingservice.service.client.GameClient;
import com.msspoker.matchmakingservice.service.client.SocialRelationshipClient;
import com.msspoker.matchmakingservice.service.store.QueueStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PartyInvitationServiceImpl implements PartyInvitationService {
    private final PartyService parties;
    private final PartyInvitationRepository invitations;
    private final PartyNotificationRepository outbox;
    private final SocialRelationshipClient social;
    private final QueueStore queues;
    private final GameClient game;
    private final MatchmakingProperties properties;
    private final Clock clock;
    private final JsonMapper json;

    @Override
    public PartyInvitationResponse invite(UUID actor, UUID target) {
        if (actor.equals(target)) throw invalid("Không thể mời chính mình.");
        requireFriends(actor, target);
        available(actor);
        available(target);
        if (parties.current(target).isPresent()) throw conflict("Người nhận đã ở trong một party.");
        var party = parties.current(actor).orElseGet(() -> parties.create(actor));
        leader(actor, party);
        mutable(party);
        lock(party.partyId());
        // Read membership again after obtaining the invitation serialization lock.
        party = parties.current(actor).orElseThrow(() -> conflict("Party đã giải tán."));
        leader(actor, party);
        mutable(party);
        var existing = invitations.findByPartyIdAndRecipientIdAndStatus(party.partyId(), target, "PENDING");
        if (existing.isPresent()) {
            var previous = existing.get();
            if (previous.getExpiresAt().isAfter(clock.instant())
                    && previous.getPasswordHash().equals(hash(party.password()))) return view(previous);
            previous.setStatus("EXPIRED");
            invitations.saveAndFlush(previous);
        }
        if (invitations.countByInviterIdAndCreatedAtAfter(actor, clock.instant().minusSeconds(60)) >= 10)
            throw new MatchmakingApiException(MatchmakingError.MM_CONFLICT, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn gửi lời mời quá nhanh. Vui lòng thử lại sau.");
        var invite = new PartyInvitationEntity();
        invite.setId(UUID.randomUUID());
        invite.setPartyId(party.partyId());
        invite.setInviterId(actor);
        invite.setRecipientId(target);
        invite.setPasswordHash(hash(party.password()));
        invite.setStatus("PENDING");
        invite.setCreatedAt(clock.instant());
        invite.setExpiresAt(clock.instant().plus(Duration.ofMinutes(5)));
        invitations.save(invite);
        notify(invite, target, "PARTY_INVITE", "Lời mời chơi cùng", "Một người bạn mời bạn vào party để chơi Normal.");
        return view(invite);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyInvitationResponse> list(UUID actor, String direction) {
        var rows = switch (direction) {
            case "INCOMING" -> invitations.findTop50ByRecipientIdOrderByCreatedAtDesc(actor);
            case "OUTGOING" -> invitations.findTop50ByInviterIdOrderByCreatedAtDesc(actor);
            default -> throw invalid("direction phải là INCOMING hoặc OUTGOING.");
        };
        return rows.stream().map(this::view).toList();
    }

    @Override
    public PartyResponse accept(UUID actor, UUID id) {
        var invite = locked(id);
        if (!invite.getRecipientId().equals(actor)) throw forbidden();
        var current = parties.current(actor);
        if (invite.getStatus().equals("ACCEPTED"))
            return current.filter(p -> p.partyId().equals(invite.getPartyId()))
                    .orElseThrow(() -> conflict("Lời mời này đã được sử dụng."));
        pending(invite);
        requireFriends(invite.getInviterId(), actor);
        var party = parties.current(invite.getInviterId())
                .filter(p -> p.partyId().equals(invite.getPartyId()))
                .orElseThrow(() -> conflict("Party không còn tồn tại."));
        leader(invite.getInviterId(), party);
        if (!hash(party.password()).equals(invite.getPasswordHash()))
            throw conflict("Party đã thay đổi. Hãy yêu cầu một lời mời mới.");
        // Heal a retry if Redis joined successfully but the SQL transaction did not commit.
        PartyResponse joined;
        if (current.isPresent()) {
            joined = current.filter(p -> p.partyId().equals(invite.getPartyId()))
                    .orElseThrow(() -> conflict("Bạn đã ở trong một party khác."));
        } else {
            available(actor);
            mutable(party);
            joined = parties.join(actor, party.partyCode(), party.password());
        }
        invite.setStatus("ACCEPTED");
        notify(invite, invite.getInviterId(), "PARTY_INVITE_ACCEPTED", "Đã chấp nhận lời mời", "Bạn của bạn đã vào party.");
        return joined;
    }

    @Override
    public void decline(UUID actor, UUID id) { resolve(actor, id, false); }

    @Override
    public void cancel(UUID actor, UUID id) { resolve(actor, id, true); }

    private void resolve(UUID actor, UUID id, boolean cancel) {
        var invite = locked(id);
        if (!(cancel ? invite.getInviterId() : invite.getRecipientId()).equals(actor)) throw forbidden();
        String result = cancel ? "CANCELLED" : "DECLINED";
        if (invite.getStatus().equals(result)) return;
        pending(invite);
        invite.setStatus(result);
    }

    private PartyInvitationEntity locked(UUID id) {
        UUID party = invitations.partyId(id).orElseThrow(() -> new MatchmakingApiException(
                MatchmakingError.MM_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy lời mời."));
        lock(party);
        return invitations.findById(id).orElseThrow();
    }

    private void lock(UUID party) {
        invitations.ensurePartyLock(party);
        invitations.lockParty(party);
    }

    private void pending(PartyInvitationEntity invite) {
        if (!invite.getStatus().equals("PENDING") || !invite.getExpiresAt().isAfter(clock.instant()))
            throw conflict("Lời mời đã hết hạn hoặc không còn chờ xử lý.");
    }

    private void requireFriends(UUID actor, UUID target) {
        if (!social.friends(actor, target)) throw forbidden();
    }

    private void available(UUID actor) {
        if (queues.find(actor).isPresent() || queues.reservation(actor).isPresent()
                || game.activeTable(actor).isPresent()) throw conflict("Người chơi đang tìm trận hoặc đang trong trận.");
    }

    private void mutable(PartyResponse party) {
        if (party.memberIds().size() >= properties.getParty().getMaxMembers()) throw conflict("Party đã đủ người.");
        party.memberIds().forEach(this::available);
    }

    private void leader(UUID actor, PartyResponse party) {
        if (!party.leaderId().equals(actor)) throw forbidden();
    }

    private PartyInvitationResponse view(PartyInvitationEntity invite) {
        String status = invite.getStatus().equals("PENDING") && !invite.getExpiresAt().isAfter(clock.instant())
                ? "EXPIRED" : invite.getStatus();
        return new PartyInvitationResponse(invite.getId(), invite.getPartyId(), invite.getInviterId(),
                invite.getRecipientId(), status, invite.getCreatedAt(), invite.getExpiresAt());
    }

    private void notify(PartyInvitationEntity invite, UUID recipient, String type, String title, String body) {
        var row = new PartyNotificationEntity();
        row.setId(UUID.randomUUID());
        row.setCreatedAt(clock.instant());
        row.setBody(json.writeValueAsString(new EventEnvelope<>(row.getId(), "notification.requested", clock.instant(),
                Map.of("accountIds", List.of(recipient), "type", type, "title", title, "body", body,
                        "data", Map.of("invitationId", invite.getId(), "partyId", invite.getPartyId(),
                                "inviterId", invite.getInviterId(), "recipientId", invite.getRecipientId(),
                                "expiresAt", invite.getExpiresAt().toString())))));
        outbox.save(row);
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static MatchmakingApiException invalid(String message) {
        return new MatchmakingApiException(MatchmakingError.MM_INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
    }
    private static MatchmakingApiException conflict(String message) {
        return new MatchmakingApiException(MatchmakingError.MM_CONFLICT, HttpStatus.CONFLICT, message);
    }
    private static MatchmakingApiException forbidden() {
        return new MatchmakingApiException(MatchmakingError.MM_FORBIDDEN, HttpStatus.FORBIDDEN, "Chỉ chủ party được mời bạn bè; chỉ đúng người nhận được chấp nhận lời mời.");
    }
}
