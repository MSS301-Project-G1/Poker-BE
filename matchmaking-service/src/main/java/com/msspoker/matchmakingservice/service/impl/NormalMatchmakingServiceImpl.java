package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.dto.response.NormalReconnectResponse;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.entity.MatchProposalPlayerEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.enums.ProposalStatus;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.mapper.QueueStatusMapper;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.model.InGameSession;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.service.NormalMatchmakingService;
import com.msspoker.matchmakingservice.service.GameClient;
import com.msspoker.matchmakingservice.service.QueueStore;
import com.msspoker.matchmakingservice.service.PartyStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
public class NormalMatchmakingServiceImpl implements NormalMatchmakingService {
    private final QueueStore queueStore;
    private final MatchProposalPlayerRepository players;
    private final Clock clock;
    private final QueueStatusMapper statusMapper;
    private final GameClient game;
    private final PartyStore partyStore;

    public NormalMatchmakingServiceImpl(
            QueueStore queueStore,
            MatchProposalPlayerRepository players,
            Clock clock,
            QueueStatusMapper statusMapper,
            GameClient game,
            PartyStore partyStore
    ) {
        this.queueStore = queueStore;
        this.players = players;
        this.clock = clock;
        this.statusMapper = statusMapper;
        this.game = game;
        this.partyStore = partyStore;
    }

    @Override
    public QueueStatusResponse join(UUID accountId) {
        MatchMode mode = MatchMode.NORMAL;
        Optional<PartySnapshot> party = partyStore.findByAccountId(accountId);
        if (party.isPresent() && !party.get().leaderId().equals(accountId)) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_NOT_PARTY_LEADER,
                    HttpStatus.FORBIDDEN,
                    "Chỉ chủ party được đưa cả nhóm vào hàng chờ Normal."
            );
        }

        List<UUID> accountIds = party.map(PartySnapshot::memberIds).orElseGet(() -> List.of(accountId));
        accountIds.forEach(this::ensureCanJoin);

        Instant joinedAt = clock.instant();
        UUID partyId = party.map(PartySnapshot::partyId).orElse(null);
        List<QueueEntry> entries = accountIds.stream()
                .map(memberId -> new QueueEntry(memberId, mode, joinedAt, partyId))
                .toList();
        boolean enqueued = party.isPresent()
                ? queueStore.enqueueParty(entries)
                : queueStore.enqueue(entries.getFirst());
        if (!enqueued) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_ALREADY_QUEUED,
                    HttpStatus.CONFLICT,
                    "Một thành viên của party đã ở trong hàng chờ."
            );
        }
        return waiting(entries.stream()
                .filter(entry -> entry.accountId().equals(accountId))
                .findFirst()
                .orElseThrow());
    }

    @Override
    public void leave(UUID accountId) {
        Optional<PartySnapshot> party = partyStore.findByAccountId(accountId);
        if (party.isPresent() && !party.get().leaderId().equals(accountId)) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_NOT_PARTY_LEADER,
                    HttpStatus.FORBIDDEN,
                    "Chỉ chủ party được đưa cả nhóm rời hàng chờ Normal."
            );
        }
        List<UUID> accountIds = party.map(PartySnapshot::memberIds).orElseGet(() -> List.of(accountId));
        if (accountIds.stream().anyMatch(players::existsActiveProposal)) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_MATCH_IN_PROGRESS,
                    HttpStatus.CONFLICT,
                    "Hệ thống đang tạo bàn cho trận này, không thể rời hàng chờ."
            );
        }

        List<QueueEntry> entries = accountIds.stream()
                .map(queueStore::find)
                .flatMap(Optional::stream)
                .toList();
        if (entries.size() != accountIds.size()) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_NOT_QUEUED,
                    HttpStatus.NOT_FOUND,
                    "Party không còn đầy đủ trong hàng chờ."
            );
        }
        QueueEntry entry = entries.stream()
                .filter(candidate -> candidate.accountId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new MatchmakingApiException(
                MatchmakingError.MM_NOT_QUEUED,
                HttpStatus.NOT_FOUND,
                "Tài khoản không ở trong hàng chờ."
        ));

        QueueStore.CancelResult result = party.isPresent()
                ? queueStore.cancelParty(entries)
                : queueStore.cancel(accountId, entry.mode());
        if (result == QueueStore.CancelResult.RESERVED) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_MATCH_IN_PROGRESS,
                    HttpStatus.CONFLICT,
                    "Hệ thống đang tạo bàn cho trận này, không thể hủy hàng chờ."
            );
        }
        if (result == QueueStore.CancelResult.NOT_FOUND) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_NOT_QUEUED,
                    HttpStatus.NOT_FOUND,
                    "Tài khoản không ở trong hàng chờ."
            );
        }
        if (queueStore.size(entry.mode()) < game.matchSettings(MatchMode.NORMAL).minPlayers()) {
            queueStore.cancelCountdown(entry.mode());
        }
    }

    private void ensureCanJoin(UUID accountId) {
        if (queueStore.find(accountId).isPresent()) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_ALREADY_QUEUED,
                    HttpStatus.CONFLICT,
                    "Một thành viên của party đã ở trong hàng chờ."
            );
        }
        if (players.existsActiveProposal(accountId) || game.activeTable(accountId).isPresent()) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_MATCH_IN_PROGRESS,
                    HttpStatus.CONFLICT,
                    "Một thành viên của party đang được tạo bàn hoặc đang ở trận khác."
            );
        }
    }

    @Override
    public QueueStatusResponse status(UUID accountId) {
        Optional<MatchProposalPlayerEntity> activeProposal =
                players.findFirstByAccountIdAndIsDeletedFalseAndProposal_StatusAndProposal_IsDeletedFalseOrderByCreatedAtDesc(
                        accountId,
                        ProposalStatus.CREATING_TABLE
                );
        if (activeProposal.isPresent()) {
            return statusMapper.proposal(activeProposal.get());
        }

        Optional<InGameSession> inGame = queueStore.inGame(accountId);
        if (inGame.isPresent()) {
            InGameSession session = inGame.get();
            Optional<TableCreatedResponse> activeTable = game.activeTable(accountId);
            if (activeTable.isPresent()
                    && session.tableId().equals(activeTable.get().tableId())
                    && session.matchId().equals(activeTable.get().matchId())) {
                return statusMapper.inGame(session);
            }
            queueStore.clearInGame(accountId);
        }

        Optional<QueueEntry> current = queueStore.find(accountId);
        if (current.isPresent()) {
            QueueEntry entry = current.get();
            Optional<UUID> reservation = queueStore.reservation(accountId);
            if (reservation.isPresent()) {
                return statusMapper.matching(entry, reservation.get());
            }
            return waiting(entry);
        }

        return QueueStatusResponse.none();
    }

    @Override
    public NormalReconnectResponse reconnect(UUID accountId) {
        Optional<InGameSession> storedSession = queueStore.inGame(accountId);
        if (storedSession.isEmpty()) {
            return NormalReconnectResponse.none();
        }

        InGameSession session = storedSession.get();
        if (session.mode() != MatchMode.NORMAL) {
            return NormalReconnectResponse.none();
        }
        Optional<TableCreatedResponse> activeTable = game.activeTable(accountId);
        if (activeTable.isEmpty()
                || !session.tableId().equals(activeTable.get().tableId())
                || !session.matchId().equals(activeTable.get().matchId())) {
            queueStore.clearInGame(accountId);
            return NormalReconnectResponse.none();
        }

        return NormalReconnectResponse.active(
                session.proposalId(),
                session.tableId(),
                session.matchId(),
                session.host(),
                session.port(),
                session.websocketPath()
        );
    }

    private QueueStatusResponse waiting(QueueEntry entry) {
        GameMatchSettingsResponse settings = game.matchSettings(MatchMode.NORMAL);
        Optional<Instant> deadline = queueStore.countdownDeadline(entry.mode());
        return statusMapper.waiting(
                entry,
                deadline.isPresent() ? "COUNTDOWN" : "WAITING",
                queueStore.position(entry.accountId(), entry.mode()).orElse(null),
                queueStore.size(entry.mode()),
                settings.minPlayers(),
                settings.maxPlayers(),
                deadline.orElse(null)
        );
    }
}
