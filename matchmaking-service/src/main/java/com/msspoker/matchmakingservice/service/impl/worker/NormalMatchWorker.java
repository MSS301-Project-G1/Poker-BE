package com.msspoker.matchmakingservice.service.impl.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.service.GameClient;
import com.msspoker.matchmakingservice.service.NormalMatchCoordinator;
import com.msspoker.matchmakingservice.service.QueueStore;
import com.msspoker.matchmakingservice.service.PartyStore;
import com.msspoker.matchmakingservice.util.MatchmakingIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "matchmaking.finder.enabled", havingValue = "true", matchIfMissing = true)
public class NormalMatchWorker {
    private static final Logger log = LoggerFactory.getLogger(NormalMatchWorker.class);

    private final QueueStore queueStore;
    private final NormalMatchCoordinator coordinator;
    private final MatchmakingProperties properties;
    private final Clock clock;
    private final PartyStore partyStore;
    private final GameClient game;

    public NormalMatchWorker(
            QueueStore queueStore,
            NormalMatchCoordinator coordinator,
            MatchmakingProperties properties,
            Clock clock,
            PartyStore partyStore,
            GameClient game
    ) {
        this.queueStore = queueStore;
        this.coordinator = coordinator;
        this.properties = properties;
        this.clock = clock;
        this.partyStore = partyStore;
        this.game = game;
    }

    @Scheduled(fixedDelayString = "${matchmaking.finder.fixed-delay-ms:1000}")
    public void find() {
        GameMatchSettingsResponse settings;
        try {
            settings = game.matchSettings(MatchMode.NORMAL);
        } catch (RuntimeException exception) {
            log.error("Cannot read NORMAL Match Settings", exception);
            return;
        }

        Optional<String> lock = queueStore.acquireFinderLock(MatchMode.NORMAL);
        if (lock.isEmpty()) return;

        List<QueueEntry> reservedEntries = null;
        UUID proposalId = null;
        try {
            int minPlayers = settings.minPlayers();
            int maxPlayers = settings.maxPlayers();
            long waitingPlayers = queueStore.size(MatchMode.NORMAL);
            int scanLimit = Math.max(maxPlayers, (int) Math.min(waitingPlayers, Integer.MAX_VALUE));
            List<QueueEntry> candidates = queueStore.oldest(MatchMode.NORMAL, scanLimit);
            List<QueueEntry> entries = selectPack(candidates, minPlayers, maxPlayers);

            if (entries.size() < minPlayers) {
                queueStore.cancelCountdown(MatchMode.NORMAL);
                return;
            }

            Instant now = clock.instant();
            {
                Optional<Instant> deadline = queueStore.countdownDeadline(MatchMode.NORMAL);
                if (deadline.isEmpty()) {
                    queueStore.startCountdown(
                            MatchMode.NORMAL,
                            now.plus(properties.getNormal().getFillTimeout())
                    );
                    return;
                }
                if (now.isBefore(deadline.get())) return;
            }

            proposalId = MatchmakingIds.newProposalId();
            if (!queueStore.reserve(MatchMode.NORMAL, entries, proposalId)) return;
            reservedEntries = entries;
            queueStore.cancelCountdown(MatchMode.NORMAL);
        } catch (RuntimeException exception) {
            log.error("Normal match finder failed", exception);
        } finally {
            queueStore.releaseFinderLock(MatchMode.NORMAL, lock.get());
        }

        if (proposalId != null && reservedEntries != null) {
            try {
                coordinator.start(proposalId, reservedEntries);
            } catch (RuntimeException exception) {
                log.error("Normal match creation failed proposalId={}", proposalId, exception);
            }
        }
    }

    private List<QueueEntry> selectPack(List<QueueEntry> candidates, int minPlayers, int maxPlayers) {
        List<List<QueueEntry>> units = queueUnits(candidates);
        for (int anchorIndex = 0; anchorIndex < units.size(); anchorIndex++) {
            List<QueueEntry> selected = new ArrayList<>(units.get(anchorIndex));
            if (selected.size() > maxPlayers) continue;

            for (int candidateIndex = 0; candidateIndex < units.size(); candidateIndex++) {
                if (candidateIndex == anchorIndex) continue;
                List<QueueEntry> unit = units.get(candidateIndex);
                if (selected.size() + unit.size() <= maxPlayers) {
                    selected.addAll(unit);
                }
                if (selected.size() == maxPlayers) return selected;
            }
            if (selected.size() >= minPlayers) return selected;
        }
        return List.of();
    }

    private List<List<QueueEntry>> queueUnits(List<QueueEntry> candidates) {
        List<List<QueueEntry>> units = new ArrayList<>();
        Set<UUID> handledParties = new HashSet<>();
        for (QueueEntry candidate : candidates) {
            if (!candidate.belongsToParty()) {
                units.add(List.of(candidate));
                continue;
            }
            if (!handledParties.add(candidate.partyId())) continue;

            partyStore.findByAccountId(candidate.accountId()).ifPresent(party -> {
                List<QueueEntry> members = party.memberIds().stream()
                        .map(queueStore::find)
                        .flatMap(Optional::stream)
                        .filter(entry -> candidate.partyId().equals(entry.partyId()))
                        .toList();
                if (members.size() == party.memberIds().size()) {
                    units.add(members);
                }
            });
        }
        return units;
    }
}
