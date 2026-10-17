package com.msspoker.gameservice.controller;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.response.MatchHistoryResponse;
import com.msspoker.gameservice.dto.response.PageResponse;
import com.msspoker.gameservice.dto.response.TableCreatedResponse;
import com.msspoker.gameservice.dto.response.MatchSettingsResponse;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.service.MatchPersistenceService;
import com.msspoker.gameservice.service.SettingsService;
import com.msspoker.gameservice.service.TableService;
import com.msspoker.gameservice.mapper.GameMapper;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalGameController {
    private final TableService tables;
    private final MatchPersistenceService matches;
    private final SettingsService settings;
    private final GameMapper mapper;

    @GetMapping("/match-settings/{mode}")
    public MatchSettingsResponse matchSettings(@PathVariable GameMode mode) {
        return mapper.settings(settings.defaults(mode));
    }

    @PostMapping("/tables")
    public TableCreatedResponse create(@Valid @RequestBody CreateTableRequest request) {
        return tables.create(request);
    }

    @GetMapping("/players/{accountId}/active-table")
    public Optional<TableCreatedResponse> activeTable(@PathVariable UUID accountId) {
        return tables.active(accountId);
    }

    @GetMapping("/matches")
    public PageResponse<MatchHistoryResponse> history(@RequestParam UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return matches.history(accountId, page, size);
    }
}
