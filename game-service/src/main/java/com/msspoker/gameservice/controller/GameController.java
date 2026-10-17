package com.msspoker.gameservice.controller;

import com.msspoker.gameservice.constant.GameHeaders;
import com.msspoker.gameservice.constant.PageConstants;
import com.msspoker.gameservice.dto.response.GameSnapshotResponse;
import com.msspoker.gameservice.dto.response.MatchHistoryResponse;
import com.msspoker.gameservice.dto.response.PageResponse;
import com.msspoker.gameservice.dto.response.TableCreatedResponse;
import com.msspoker.gameservice.service.GameplayService;
import com.msspoker.gameservice.service.MatchPersistenceService;
import com.msspoker.gameservice.service.TableService;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/game")
public class GameController {
    private final TableService tables;
    private final GameplayService gameplay;
    private final MatchPersistenceService matches;

    @GetMapping("/me/active-table")
    public Optional<TableCreatedResponse> active(@RequestAttribute(GameHeaders.USER_ID) UUID accountId) {
        return tables.active(accountId);
    }

    @GetMapping("/tables/{tableId}")
    public GameSnapshotResponse snapshot(@PathVariable UUID tableId, @RequestAttribute(GameHeaders.USER_ID) UUID accountId) {
        return gameplay.snapshot(tableId, accountId);
    }

    @GetMapping("/tables/{tableId}/result")
    public MatchHistoryResponse result(@PathVariable UUID tableId, @RequestAttribute(GameHeaders.USER_ID) UUID accountId) {
        return matches.result(tableId, accountId);
    }

    @GetMapping("/matches")
    public PageResponse<MatchHistoryResponse> history(@RequestParam UUID accountId,
            @RequestParam(defaultValue = PageConstants.FIRST_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = PageConstants.DEFAULT_SIZE) @Min(1) @Max(PageConstants.MAX_SIZE) int size) {
        return matches.history(accountId, page, size);
    }
}
