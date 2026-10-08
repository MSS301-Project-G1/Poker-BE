package com.msspoker.gameservice.api;

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
    public GameDtos.TableCreated create(@Valid @RequestBody GameDtos.CreateTable request) {
        return tables.create(request);
    }

    @GetMapping("/matches")
    public GameDtos.PageResponse<GameDtos.MatchView> history(@RequestParam UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return matches.history(accountId, page, size);
    }
}
