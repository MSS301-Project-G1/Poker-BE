package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.dto.request.CreateTableRequest;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.exception.GameCallRejectedException;
import com.msspoker.matchmakingservice.exception.GameCallUnknownException;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.model.InGameSession;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.service.GameClient;
import com.msspoker.matchmakingservice.service.NormalMatchCoordinator;
import com.msspoker.matchmakingservice.service.ProposalPersistenceService;
import com.msspoker.matchmakingservice.service.QueueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NormalMatchCoordinatorImpl implements NormalMatchCoordinator {
    private static final Logger log = LoggerFactory.getLogger(NormalMatchCoordinatorImpl.class);

    private final ProposalPersistenceService persistence;
    private final GameClient game;
    private final QueueStore queueStore;
    private final MatchmakingProperties properties;

    public NormalMatchCoordinatorImpl(
            ProposalPersistenceService persistence,
            GameClient game,
            QueueStore queueStore,
            MatchmakingProperties properties
    ) {
        this.persistence = persistence;
        this.game = game;
        this.queueStore = queueStore;
        this.properties = properties;
    }

    @Override
    public void start(UUID proposalId, List<QueueEntry> entries) {
        try {
            persistence.createNormal(proposalId, entries);
        } catch (RuntimeException exception) {
            queueStore.requeue(entries);
            throw exception;
        }
        // The last acceptance starts table creation through retry().
    }

    @Override
    public void retry(UUID proposalId, List<QueueEntry> entries) {
        createTable(proposalId, entries);
    }

    private void createTable(UUID proposalId, List<QueueEntry> entries) {
        CreateTableRequest request = CreateTableRequest.normal(
                proposalId,
                entries.stream().map(QueueEntry::accountId).toList()
        );
        try {
            TableCreatedResponse response = game.createTable(request);
            queueStore.complete(entries, session(proposalId, response.tableId(), response.matchId()));
            persistence.ready(proposalId, response.tableId(), response.matchId());
            log.info("Normal match ready proposalId={} tableId={} matchId={}",
                    proposalId, response.tableId(), response.matchId());
        } catch (GameCallRejectedException exception) {
            persistence.failed(proposalId, "GAME_REJECTED");
            queueStore.requeue(entries);
            log.warn("Game rejected normal proposal proposalId={}", proposalId, exception);
        } catch (GameCallUnknownException exception) {
            // Do not requeue: Game may have created the table before the response was lost.
            // Recovery retries with the same sourceId, which is idempotent in game-service.
            log.warn("Normal proposal awaits Game reconciliation proposalId={}", proposalId, exception);
        }
    }

    private InGameSession session(UUID proposalId, UUID tableId, UUID matchId) {
        return new InGameSession(
                MatchMode.NORMAL,
                proposalId,
                tableId,
                matchId,
                properties.getGame().getPublicHost(),
                properties.getGame().getPublicPort(),
                properties.getGame().getWebsocketPath()
        );
    }
}
