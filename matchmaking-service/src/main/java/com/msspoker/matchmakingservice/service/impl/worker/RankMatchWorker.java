package com.msspoker.matchmakingservice.service.impl.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.service.GameClient;
import com.msspoker.matchmakingservice.service.ProposalPersistenceService;
import com.msspoker.matchmakingservice.service.QueueStore;
import com.msspoker.matchmakingservice.util.MatchmakingIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "matchmaking.finder.enabled", havingValue = "true", matchIfMissing = true)
public class RankMatchWorker {
    private static final Logger log = LoggerFactory.getLogger(RankMatchWorker.class);

    private final QueueStore queueStore;
    private final ProposalPersistenceService persistence;
    private final GameClient game;
    private final MatchmakingProperties properties;
    private final Clock clock;

    public RankMatchWorker(
            QueueStore queueStore,
            ProposalPersistenceService persistence,
            GameClient game,
            MatchmakingProperties properties,
            Clock clock
    ) {
        this.queueStore = queueStore;
        this.persistence = persistence;
        this.game = game;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${matchmaking.finder.fixed-delay-ms:1000}")
    public void find() {
        GameMatchSettingsResponse settings;
        try {
            settings = game.matchSettings(MatchMode.RANK);
        } catch (RuntimeException exception) {
            log.error("Cannot read RANK Match Settings", exception);
            return;
        }

        Optional<String> lock = queueStore.acquireFinderLock(MatchMode.RANK);
        if (lock.isEmpty()) return;

        List<QueueEntry> reserved = null;
        UUID proposalId = null;
        Instant acceptDeadline = null;
        try {
            long waiting = queueStore.size(MatchMode.RANK);
            if (waiting == 0) {
                queueStore.cancelCountdown(MatchMode.RANK);
                return;
            }
            int scanLimit = (int) Math.min(waiting, Integer.MAX_VALUE);
            List<QueueEntry> candidates = queueStore.oldest(MatchMode.RANK, scanLimit);
            List<QueueEntry> entries = select(candidates, settings.maxPlayers());

            if (entries.size() < settings.minPlayers()) {
                queueStore.cancelCountdown(MatchMode.RANK);
                return;
            }

            Instant now = clock.instant();
            if (entries.size() < settings.maxPlayers()) {
                Optional<Instant> deadline = queueStore.countdownDeadline(MatchMode.RANK);
                if (deadline.isEmpty()) {
                    queueStore.startCountdown(MatchMode.RANK, now.plus(properties.getRank().getFillTimeout()));
                    return;
                }
                if (now.isBefore(deadline.get())) return;
            }

            proposalId = MatchmakingIds.newProposalId();
            if (!queueStore.reserve(MatchMode.RANK, entries, proposalId)) return;
            reserved = entries;
            acceptDeadline = now.plus(properties.getRank().getAcceptTimeout());
            queueStore.cancelCountdown(MatchMode.RANK);
        } catch (RuntimeException exception) {
            log.error("Rank match finder failed", exception);
        } finally {
            queueStore.releaseFinderLock(MatchMode.RANK, lock.get());
        }

        if (proposalId == null || reserved == null || acceptDeadline == null) return;
        try {
            persistence.createRank(
                    proposalId,
                    reserved,
                    properties.getRank().getEntryFee(),
                    acceptDeadline
            );
        } catch (RuntimeException exception) {
            log.error("Cannot persist Rank proposal proposalId={}", proposalId, exception);
            queueStore.requeue(reserved);
        }
    }

    List<QueueEntry> select(List<QueueEntry> candidates, int maxPlayers) {
        if (candidates.isEmpty()) return List.of();
        QueueEntry anchor = candidates.getFirst();
        if (anchor.elo() == null) return List.of();

        int eloWindow = properties.getRank().getEloWindow();
        Comparator<QueueEntry> priority = Comparator
                .comparingLong((QueueEntry entry) -> Math.abs((long) entry.elo() - anchor.elo()))
                .thenComparingInt(entry -> Math.abs(entry.rankForm() - anchor.rankForm()))
                .thenComparing(QueueEntry::joinedAt)
                .thenComparing(QueueEntry::accountId);

        List<QueueEntry> compatible = candidates.stream()
                .filter(entry -> entry.mode() == MatchMode.RANK && entry.elo() != null)
                .filter(entry -> Math.abs((long) entry.elo() - anchor.elo()) <= eloWindow)
                .filter(entry -> !entry.accountId().equals(anchor.accountId()))
                .sorted(priority)
                .limit(Math.max(0, maxPlayers - 1L))
                .toList();

        List<QueueEntry> selected = new ArrayList<>(1 + compatible.size());
        selected.add(anchor);
        selected.addAll(compatible);
        return selected;
    }
}
