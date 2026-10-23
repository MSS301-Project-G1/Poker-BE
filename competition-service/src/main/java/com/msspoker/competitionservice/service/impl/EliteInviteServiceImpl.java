package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.dto.response.RankedPlayerResponse;
import com.msspoker.competitionservice.entity.TournamentEntity;
import com.msspoker.competitionservice.entity.TournamentEntryEntity;
import com.msspoker.competitionservice.enums.EntryStatus;
import com.msspoker.competitionservice.enums.NotificationType;
import com.msspoker.competitionservice.enums.TournamentStatus;
import com.msspoker.competitionservice.enums.TournamentType;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.mapper.TournamentMapper;
import com.msspoker.competitionservice.repository.TournamentEntryRepository;
import com.msspoker.competitionservice.repository.TournamentRepository;
import com.msspoker.competitionservice.service.EliteInviteService;
import com.msspoker.competitionservice.service.NotificationService;
import com.msspoker.competitionservice.service.client.RankingClient;
import com.msspoker.competitionservice.validator.EliteInviteValidator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The leaderboard is read outside the tournament lock, so a slow ranking-service never holds it. */
@Service
@RequiredArgsConstructor
public class EliteInviteServiceImpl implements EliteInviteService {
    private final TournamentRepository tournaments;
    private final TournamentEntryRepository entries;
    private final TournamentMapper mapper;
    private final EliteInviteValidator invites;
    private final RankingClient ranking;
    private final NotificationService notifications;
    private final TransactionTemplate transactions;

    @Override
    public void invite(UUID tournamentId) {
        TournamentEntity tournament = tournaments.findByIdAndIsDeletedFalse(tournamentId)
                .orElseThrow(CompetitionExceptions::tournamentNotFound);
        List<RankedPlayerResponse> top = ranking.top(Math.min(tournament.getSlots(), CompetitionConstants.MAX_RANKING_LIMIT));
        transactions.executeWithoutResult(status -> {
            TournamentEntity locked = lock(tournamentId);
            if (!awaitingStart(locked) || locked.getInvitesSentAt() != null) return;
            Instant now = Instant.now();
            sendInvites(locked, top, Set.of(), locked.getSlots(), now);
            locked.setInvitesSentAt(now);
        });
    }

    /**
     * Ranks after the invited ones are read with a margin of every existing entry, so players already
     * invited (confirmed or expired) are skipped and the next ranks fill the open places.
     */
    @Override
    public void replace(UUID tournamentId) {
        Integer open = transactions.execute(status -> expireUnconfirmed(tournamentId));
        if (open == null || open == 0) return;
        long entryCount = entries.countByTournamentId(tournamentId);
        List<RankedPlayerResponse> top = ranking.top((int) Math.min(entryCount + open, CompetitionConstants.MAX_RANKING_LIMIT));
        transactions.executeWithoutResult(status -> {
            TournamentEntity locked = lock(tournamentId);
            if (!awaitingStart(locked) || locked.getReplacementsSentAt() != null) return;
            List<TournamentEntryEntity> current = entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(tournamentId);
            Set<UUID> known = current.stream().map(TournamentEntryEntity::getAccountId).collect(Collectors.toSet());
            Instant now = Instant.now();
            sendInvites(locked, top, known, openPlaces(locked, current), now);
            locked.setReplacementsSentAt(now);
        });
    }

    /**
     * Runs even when ranking-service is down, so the deadline holds. Returns the places to fill; with
     * none open the replacement step is finished here.
     */
    private int expireUnconfirmed(UUID tournamentId) {
        TournamentEntity tournament = lock(tournamentId);
        if (!awaitingStart(tournament) || tournament.getInvitesSentAt() == null
                || tournament.getReplacementsSentAt() != null) {
            return 0;
        }
        Instant now = Instant.now();
        List<TournamentEntryEntity> current = entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(tournamentId);
        current.stream().filter(entry -> entry.getStatus() == EntryStatus.INVITED && invites.expired(tournament, entry, now))
                .forEach(entry -> entry.setStatus(EntryStatus.EXPIRED));
        int open = openPlaces(tournament, current);
        if (open == 0) tournament.setReplacementsSentAt(now);
        return open;
    }

    /** Places not held by a confirmed player or an invitation that may still be confirmed. */
    private int openPlaces(TournamentEntity tournament, List<TournamentEntryEntity> current) {
        long held = current.stream().filter(entry -> entry.getStatus() == EntryStatus.CONFIRMED
                || entry.getStatus() == EntryStatus.INVITED).count();
        return (int) Math.max(0, tournament.getSlots() - held);
    }

    /** Invites the best ranked players without an entry, in leaderboard order. */
    private void sendInvites(TournamentEntity tournament, List<RankedPlayerResponse> top, Set<UUID> known, int places,
                             Instant now) {
        List<UUID> invited = top.stream().map(RankedPlayerResponse::accountId)
                .filter(accountId -> accountId != null && !known.contains(accountId)).distinct().limit(places).toList();
        invited.forEach(accountId -> entries.save(mapper.newEntry(tournament.getId(), accountId, EntryStatus.INVITED, now)));
        notifications.notify(NotificationType.TOURNAMENT_INVITE, invited, "Lời mời Giải Cao thủ",
                "Bạn được mời tham dự " + tournament.getName() + ". Hãy xác nhận để giữ suất thi đấu.",
                Map.of("tournamentId", tournament.getId(), "startAt", tournament.getStartAt().toString(),
                        "confirmBy", invites.confirmBy(tournament, now).toString()));
    }

    private boolean awaitingStart(TournamentEntity tournament) {
        return tournament.getType() == TournamentType.ELITE && tournament.getStatus() == TournamentStatus.SCHEDULED
                && Instant.now().isBefore(tournament.getStartAt());
    }

    private TournamentEntity lock(UUID tournamentId) {
        return tournaments.lockById(tournamentId).orElseThrow(CompetitionExceptions::tournamentNotFound);
    }
}
