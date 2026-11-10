package com.msspoker.competitionservice.service.impl.job;

import com.msspoker.competitionservice.dto.request.CreateTableRequest;
import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.entity.TournamentEntity;
import com.msspoker.competitionservice.entity.TournamentSeatEntity;
import com.msspoker.competitionservice.entity.TournamentTableEntity;
import com.msspoker.competitionservice.enums.GameMode;
import com.msspoker.competitionservice.enums.NotificationType;
import com.msspoker.competitionservice.enums.TableStatus;
import com.msspoker.competitionservice.enums.TournamentRound;
import com.msspoker.competitionservice.repository.TournamentRepository;
import com.msspoker.competitionservice.repository.TournamentSeatRepository;
import com.msspoker.competitionservice.repository.TournamentTableRepository;
import com.msspoker.competitionservice.service.client.GameClient;
import com.msspoker.competitionservice.service.NotificationService;
import com.msspoker.competitionservice.service.job.TournamentTableLauncher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentTableLauncherImpl implements TournamentTableLauncher {
    private final TournamentTableRepository tables;
    private final TournamentSeatRepository seats;
    private final TournamentRepository tournaments;
    private final GameClient game;
    private final NotificationService notifications;
    private final TransactionTemplate transactions;
    private final ReentrantLock launching = new ReentrantLock();

    /**
     * The tournament table id is the CreateTable sourceId and the roster never changes, so a launch
     * repeated after a crash or timeout returns the table game-service already opened.
     */
    @Scheduled(fixedDelayString = "${competition.tournament.launch-millis:2000}")
    @Override
    public void launchPending() {
        if (!launching.tryLock()) return;
        try {
            for (TournamentTableEntity pending : tables.findTop10ByStatusOrderByCreatedAtAscTableIndexAscIdAsc(TableStatus.PENDING)) {
                TournamentEntity tournament = tournaments.findById(pending.getTournamentId()).orElseThrow();
                List<TournamentSeatEntity> roster = seats.findByTournamentTableIdOrderBySeatIndex(pending.getId());
                CreateTableRequest request = new CreateTableRequest(GameMode.TOURNAMENT, pending.getId(),
                        roster.stream().map(TournamentSeatEntity::getAccountId).toList(), null, tournament.getEntryFee());
                try {
                    TableCreatedResponse created = game.createTable(request);
                    transactions.executeWithoutResult(status -> markPlaying(pending, created, request));
                } catch (Exception ex) {
                    log.warn("Tournament table {} not opened yet; will retry", pending.getId(), ex);
                }
            }
        } finally {
            launching.unlock();
        }
    }

    private void markPlaying(TournamentTableEntity pending, TableCreatedResponse created, CreateTableRequest request) {
        TournamentTableEntity table = tables.lockById(pending.getId()).orElseThrow();
        if (table.getStatus() != TableStatus.PENDING) return;
        table.setStatus(TableStatus.PLAYING);
        table.setTableId(created.tableId());
        table.setMatchId(created.matchId());
        notifications.notify(NotificationType.TOURNAMENT_TABLE_READY, request.playerIds(), "Bàn giải đấu đã sẵn sàng",
                "Vào bàn để bắt đầu " + (table.getRound() == TournamentRound.FINAL ? "chung kết." : "vòng loại."),
                Map.of("tournamentId", table.getTournamentId(), "tournamentTableId", table.getId(),
                        "round", table.getRound(), "tableId", created.tableId()));
    }
}
