package com.msspoker.gameservice.controller;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.response.MatchHistoryResponse;
import com.msspoker.gameservice.dto.response.PageResponse;
import com.msspoker.gameservice.dto.response.TableCreatedResponse;
import com.msspoker.gameservice.service.MatchPersistenceService;
import com.msspoker.gameservice.service.TableService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalGameController {
    private final TableService tables;
    private final MatchPersistenceService matches;

    @PostMapping("/tables")
    public TableCreatedResponse create(@Valid @RequestBody CreateTableRequest request) {
        return tables.create(request);
    }

    @GetMapping("/matches")
    public PageResponse<MatchHistoryResponse> history(@RequestParam UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return matches.history(accountId, page, size);
    }
}
