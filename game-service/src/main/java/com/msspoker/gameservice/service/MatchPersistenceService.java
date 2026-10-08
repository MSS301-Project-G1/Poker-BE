package com.msspoker.gameservice.service;

import com.msspoker.gameservice.constant.GameEventConstants;
import com.msspoker.gameservice.dto.event.EventEnvelope;
import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.response.MatchHistoryResponse;
import com.msspoker.gameservice.dto.response.PageResponse;
import com.msspoker.gameservice.entity.MatchEntity;
import com.msspoker.gameservice.entity.MatchPlayerEntity;
import com.msspoker.gameservice.entity.OutboxEntity;
import com.msspoker.gameservice.enums.MatchStatus;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.mapper.GameMapper;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.model.game.PokerTable;
import com.msspoker.gameservice.repository.MatchPlayerRepository;
import com.msspoker.gameservice.repository.MatchRepository;
import com.msspoker.gameservice.repository.OutboxRepository;
import com.msspoker.gameservice.util.GameIds;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchPersistenceService {
    private final MatchRepository matches;
    private final MatchPlayerRepository players;
    private final OutboxRepository outbox;
    private final GameMapper mapper;
    private final JsonMapper json;

    @Transactional
    public MatchEntity started(CreateTableRequest request, PokerTable table) {
        MatchEntity match = matches.save(mapper.match(request, table.getTableId(), table.getMatchId(), Instant.now(),
                json.writeValueAsString(request)));
        players.saveAll(table.getSeats().stream().map(seat -> mapper.player(seat, match.getId())).toList());
        enqueue(GameEventConstants.STARTED, mapper.started(match, request.playerIds()));
        return match;
    }

    @Transactional
    public void finished(ManagedTable table) {
        MatchEntity match = matches.findById(table.getEngine().getMatchId()).orElseThrow(GameExceptions::tableNotFound);
        if (match.getStatus() == MatchStatus.FINISHED) return;
        match.setStatus(MatchStatus.FINISHED);
        match.setFinishedAt(Instant.now());
        List<MatchPlayerEntity> rows = players.findByMatchIdOrderBySeatIndex(match.getId());
        rows.forEach(row -> mapper.finishPlayer(table.getEngine().getSeats().get(row.getSeatIndex()), row));
        enqueue(GameEventConstants.FINISHED, mapper.finished(match, rows.stream().map(mapper::placement).toList()));
    }

    @Transactional(readOnly = true)
    public Optional<MatchEntity> existing(CreateTableRequest request) {
        Optional<MatchEntity> match = matches.findByModeAndSourceId(request.mode(), request.sourceId());
        match.ifPresent(existing -> {
            if (!existing.getRequestFingerprint().equals(json.writeValueAsString(request))) {
                throw GameExceptions.sourceConflict();
            }
        });
        return match;
    }

    @Transactional(readOnly = true)
    public PageResponse<MatchHistoryResponse> history(UUID accountId, int page, int size) {
        Page<MatchPlayerEntity> membership = players.findByAccountIdAndIsDeletedFalse(accountId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        List<UUID> ids = membership.getContent().stream().map(MatchPlayerEntity::getMatchId).toList();
        Map<UUID, MatchEntity> byId = matches.findAllById(ids).stream()
                .collect(Collectors.toMap(MatchEntity::getId, match -> match));
        Map<UUID, List<MatchPlayerEntity>> rosters = ids.isEmpty() ? Map.of()
                : players.findByMatchIdIn(ids).stream().collect(Collectors.groupingBy(MatchPlayerEntity::getMatchId));
        List<MatchHistoryResponse> content = ids.stream().map(id -> mapper.view(byId.get(id),
                rosters.getOrDefault(id, List.of()).stream().sorted(Comparator.comparingInt(MatchPlayerEntity::getSeatIndex))
                        .map(mapper::placement).toList())).toList();
        return new PageResponse<>(content, page, size, membership.getTotalElements(), membership.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MatchHistoryResponse result(UUID tableId, UUID accountId) {
        // Only persisted roster members can access the finished match.
        MatchEntity match = matches.findByTableId(tableId).orElseThrow(GameExceptions::tableNotFound);
        List<MatchPlayerEntity> roster = players.findByMatchIdOrderBySeatIndex(match.getId());
        if (roster.stream().noneMatch(p -> p.getAccountId().equals(accountId))) throw GameExceptions.forbidden();
        return mapper.view(match, roster.stream().map(mapper::placement).toList());
    }

    @Transactional
    public void cancelInterruptedMatches() {
        // Live hole cards are intentionally not persisted; do not resume a stale RUNNING match.
        matches.findByStatus(MatchStatus.RUNNING).forEach(match -> {
            match.setStatus(MatchStatus.CANCELLED);
            match.setFinishedAt(Instant.now());
        });
    }

    private void enqueue(String eventType, Object payload) {
        OutboxEntity row = new OutboxEntity();
        row.setId(GameIds.next());
        row.setEventType(eventType);
        row.setOccurredAt(Instant.now());
        row.setBody(json.writeValueAsString(new EventEnvelope(row.getId(), eventType, row.getOccurredAt(), payload)));
        outbox.save(row);
    }
}
