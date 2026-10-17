package com.msspoker.competitionservice.controller;

import com.msspoker.competitionservice.constant.CompetitionHeaders;
import com.msspoker.competitionservice.dto.request.CreateTournamentRequest;
import com.msspoker.competitionservice.dto.response.TournamentResponse;
import com.msspoker.competitionservice.service.TournamentService;
import com.msspoker.competitionservice.validator.AdminAccessValidator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tournaments")
public class AdminTournamentController {
    private final TournamentService tournaments;
    private final AdminAccessValidator admin;

    @PostMapping
    public TournamentResponse create(@RequestHeader(CompetitionHeaders.USER_ID) UUID adminId,
            @RequestHeader(value = CompetitionHeaders.USER_ROLE, required = false) String role,
            @Valid @RequestBody CreateTournamentRequest request) {
        admin.validate(role);
        return tournaments.create(adminId, request);
    }

    @PostMapping("/{tournamentId}/start")
    public TournamentResponse start(@PathVariable UUID tournamentId,
            @RequestHeader(value = CompetitionHeaders.USER_ROLE, required = false) String role) {
        admin.validate(role);
        return tournaments.start(tournamentId);
    }

    @PostMapping("/{tournamentId}/cancel")
    public TournamentResponse cancel(@PathVariable UUID tournamentId,
            @RequestHeader(value = CompetitionHeaders.USER_ROLE, required = false) String role) {
        admin.validate(role);
        return tournaments.cancel(tournamentId);
    }
}
