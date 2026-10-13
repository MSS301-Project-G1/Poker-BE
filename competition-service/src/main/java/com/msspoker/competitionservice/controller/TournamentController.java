package com.msspoker.competitionservice.controller;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.constant.CompetitionHeaders;
import com.msspoker.competitionservice.dto.response.PageResponse;
import com.msspoker.competitionservice.dto.response.TournamentResponse;
import com.msspoker.competitionservice.dto.response.TournamentSummaryResponse;
import com.msspoker.competitionservice.service.TournamentService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tournaments")
public class TournamentController {
    private final TournamentService tournaments;

    @GetMapping
    public PageResponse<TournamentSummaryResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CompetitionConstants.MAX_PAGE_SIZE) int size) {
        return tournaments.list(page, size);
    }

    @GetMapping("/{tournamentId}")
    public TournamentResponse get(@PathVariable UUID tournamentId) {
        return tournaments.get(tournamentId);
    }

    @PostMapping("/{tournamentId}/register")
    public TournamentResponse register(@PathVariable UUID tournamentId,
            @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId) {
        return tournaments.register(tournamentId, accountId);
    }

    @PostMapping("/{tournamentId}/confirm")
    public TournamentResponse confirm(@PathVariable UUID tournamentId,
            @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId) {
        return tournaments.confirm(tournamentId, accountId);
    }
}
