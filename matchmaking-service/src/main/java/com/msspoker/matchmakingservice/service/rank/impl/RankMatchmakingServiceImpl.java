package com.msspoker.matchmakingservice.service.rank.impl;

import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.dto.response.PlayerRankingResponse;
import com.msspoker.matchmakingservice.dto.response.AccountRestrictionsResponse;
import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.dto.response.NormalReconnectResponse;
import com.msspoker.matchmakingservice.entity.MatchProposalPlayerEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.enums.ProposalStatus;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.mapper.QueueStatusMapper;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.model.InGameSession;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.service.client.GameClient;
import com.msspoker.matchmakingservice.service.store.PartyStore;
import com.msspoker.matchmakingservice.service.store.QueueStore;
import com.msspoker.matchmakingservice.service.rank.RankMatchmakingService;
import com.msspoker.matchmakingservice.service.client.RankingClient;
import com.msspoker.matchmakingservice.service.client.RestrictionClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RankMatchmakingServiceImpl implements RankMatchmakingService {
    private static final List<ProposalStatus> ACTIVE_STATUSES = List.of(
            ProposalStatus.WAITING_ACCEPTANCE,
            ProposalStatus.CREATING_TABLE
    );

    private final QueueStore queueStore;
    private final MatchProposalPlayerRepository players;
    private final RankingClient ranking;
    private final GameClient game;
    private final PartyStore parties;
    private final QueueStatusMapper statusMapper;
    private final Clock clock;
    private final RestrictionClient restrictions;

    public RankMatchmakingServiceImpl(
            QueueStore queueStore,
            MatchProposalPlayerRepository players,
            RankingClient ranking,
            GameClient game,
            PartyStore parties,
            QueueStatusMapper statusMapper,
            Clock clock,
            RestrictionClient restrictions
    ) {
        this.queueStore = queueStore;
        this.players = players;
        this.ranking = ranking;
        this.game = game;
        this.parties = parties;
        this.statusMapper = statusMapper;
        this.clock = clock;
        this.restrictions = restrictions;
    }

    @Override
    public QueueStatusResponse join(UUID accountId) {
        if (parties.findByAccountId(accountId).isPresent()) {
            throw error(MatchmakingError.MM_MODE_NOT_AVAILABLE, HttpStatus.CONFLICT,
                    "Party hiện chỉ được tìm trận Normal.");
        }
        if (queueStore.find(accountId).isPresent()) {
            throw error(MatchmakingError.MM_ALREADY_QUEUED, HttpStatus.CONFLICT,
                    "Tài khoản đã ở trong hàng chờ.");
        }
        if (players.existsActiveProposal(accountId)) {
            throw error(MatchmakingError.MM_ACTIVE_PROPOSAL, HttpStatus.CONFLICT,
                    "Tài khoản đang có proposal chưa kết thúc.");
        }
        if (game.activeTable(accountId).isPresent()) {
            throw error(MatchmakingError.MM_MATCH_IN_PROGRESS, HttpStatus.CONFLICT,
                    "Tài khoản đang ở trong một trận.");
        }

        queueStore.rankCooldown(accountId)
                .filter(deadline -> deadline.isAfter(clock.instant()))
                .ifPresent(deadline -> {
                    throw error(MatchmakingError.MM_RANK_RESTRICTED, HttpStatus.CONFLICT,
                            "Người chơi đang trong cooldown Rank đến " + deadline + ".");
                });
        AccountRestrictionsResponse restriction = restrictions.restrictions(accountId);
        if (restriction.banned() || (restriction.rankBannedUntil() != null
                && restriction.rankBannedUntil().isAfter(clock.instant()))) {
            throw error(MatchmakingError.MM_RANK_RESTRICTED, HttpStatus.FORBIDDEN,
                    "Tài khoản đang bị hạn chế tham gia Rank.");
        }

        PlayerRankingResponse snapshot = ranking.ranking(accountId);
        QueueEntry entry = QueueEntry.rank(
                accountId,
                clock.instant(),
                snapshot.elo(),
                snapshot.winStreak(),
                snapshot.lossStreak()
        );
        if (!queueStore.enqueue(entry)) {
            throw error(MatchmakingError.MM_ALREADY_QUEUED, HttpStatus.CONFLICT,
                    "Tài khoản đã ở trong hàng chờ.");
        }
        return waiting(entry);
    }

    @Override
    public void leave(UUID accountId) {
        if (players.existsActiveProposal(accountId)) {
            throw error(MatchmakingError.MM_ACTIVE_PROPOSAL, HttpStatus.CONFLICT,
                    "Proposal đã được tạo, không thể rời queue bằng API này.");
        }
        QueueEntry entry = queueStore.find(accountId)
                .filter(candidate -> candidate.mode() == MatchMode.RANK)
                .orElseThrow(() -> error(MatchmakingError.MM_NOT_QUEUED, HttpStatus.NOT_FOUND,
                        "Tài khoản không ở trong hàng chờ Rank."));
        QueueStore.CancelResult result = queueStore.cancel(accountId, MatchMode.RANK);
        if (result == QueueStore.CancelResult.RESERVED) {
            throw error(MatchmakingError.MM_MATCH_IN_PROGRESS, HttpStatus.CONFLICT,
                    "Hệ thống đang tạo proposal, không thể hủy hàng chờ.");
        }
        if (result == QueueStore.CancelResult.NOT_FOUND) {
            throw error(MatchmakingError.MM_NOT_QUEUED, HttpStatus.NOT_FOUND,
                    "Tài khoản không ở trong hàng chờ Rank.");
        }
        GameMatchSettingsResponse settings = game.matchSettings(MatchMode.RANK);
        if (queueStore.size(entry.mode()) < settings.minPlayers()) {
            queueStore.cancelCountdown(entry.mode());
        }
    }

    @Override
    public QueueStatusResponse status(UUID accountId) {
        Optional<MatchProposalPlayerEntity> proposal = players
                .findFirstByAccountIdAndIsDeletedFalseAndProposal_StatusInAndProposal_IsDeletedFalseOrderByCreatedAtDesc(
                        accountId, ACTIVE_STATUSES
                );
        if (proposal.isPresent() && proposal.get().getProposal().getMode() == MatchMode.RANK) {
            return proposal.get().getProposal().getStatus() == ProposalStatus.WAITING_ACCEPTANCE
                    ? statusMapper.rankProposal(proposal.get())
                    : statusMapper.proposal(proposal.get());
        }

        Optional<InGameSession> inGame = queueStore.inGame(accountId)
                .filter(session -> session.mode() == MatchMode.RANK);
        if (inGame.isPresent()) {
            InGameSession session = inGame.get();
            var activeTable = game.activeTable(accountId);
            if (activeTable.isPresent()
                    && session.tableId().equals(activeTable.get().tableId())
                    && session.matchId().equals(activeTable.get().matchId())) {
                return statusMapper.inGame(session);
            }
            queueStore.clearInGame(accountId);
        }

        Optional<QueueEntry> current = queueStore.find(accountId)
                .filter(entry -> entry.mode() == MatchMode.RANK);
        if (current.isEmpty()) return QueueStatusResponse.none();
        Optional<UUID> reservation = queueStore.reservation(accountId);
        return reservation.map(uuid -> statusMapper.matching(current.get(), uuid))
                .orElseGet(() -> waiting(current.get()));
    }

    @Override
    public NormalReconnectResponse reconnect(UUID accountId) {
        Optional<InGameSession> stored = queueStore.inGame(accountId)
                .filter(session -> session.mode() == MatchMode.RANK);
        if (stored.isEmpty()) return NormalReconnectResponse.none(MatchMode.RANK);

        InGameSession session = stored.get();
        var activeTable = game.activeTable(accountId);
        if (activeTable.isEmpty()
                || !session.tableId().equals(activeTable.get().tableId())
                || !session.matchId().equals(activeTable.get().matchId())) {
            queueStore.clearInGame(accountId);
            return NormalReconnectResponse.none(MatchMode.RANK);
        }
        return NormalReconnectResponse.active(
                MatchMode.RANK,
                session.proposalId(),
                session.tableId(),
                session.matchId(),
                session.host(),
                session.port(),
                session.websocketPath()
        );
    }

    private QueueStatusResponse waiting(QueueEntry entry) {
        GameMatchSettingsResponse settings = game.matchSettings(MatchMode.RANK);
        Optional<Instant> deadline = queueStore.countdownDeadline(MatchMode.RANK);
        return statusMapper.waiting(
                entry,
                deadline.isPresent() ? "COUNTDOWN" : "WAITING",
                queueStore.position(entry.accountId(), MatchMode.RANK).orElse(null),
                queueStore.size(MatchMode.RANK),
                settings.minPlayers(),
                settings.maxPlayers(),
                deadline.orElse(null),
                queueStore.oldest(MatchMode.RANK, settings.maxPlayers()).stream().map(QueueEntry::accountId).toList()
        );
    }

    private MatchmakingApiException error(MatchmakingError code, HttpStatus status, String message) {
        return new MatchmakingApiException(code, status, message);
    }
}
