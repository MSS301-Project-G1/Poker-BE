package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.constant.RankingHeaders;
import com.msspoker.rankingservice.dto.request.CreateSeasonRequest;
import com.msspoker.rankingservice.dto.request.UpdateSeasonRewardsRequest;
import com.msspoker.rankingservice.dto.response.SeasonDetailResponse;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.validator.AdminAccessValidator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/seasons")
public class AdminSeasonController {
    private final SeasonService seasons;
    private final AdminAccessValidator admin;

    @GetMapping
    public List<SeasonDetailResponse> list(@RequestHeader(value = RankingHeaders.USER_ROLE, required = false) String role) {
        admin.validate(role);
        return seasons.list();
    }

    @PostMapping
    public SeasonDetailResponse create(@RequestHeader(value = RankingHeaders.USER_ROLE, required = false) String role,
            @Valid @RequestBody CreateSeasonRequest request) {
        admin.validate(role);
        return seasons.create(request);
    }

    @PutMapping("/{seasonId}/rewards")
    public SeasonDetailResponse updateRewards(@PathVariable UUID seasonId,
            @RequestHeader(value = RankingHeaders.USER_ROLE, required = false) String role,
            @Valid @RequestBody UpdateSeasonRewardsRequest request) {
        admin.validate(role);
        return seasons.updateRewards(seasonId, request);
    }

    @PostMapping("/close")
    public SeasonDetailResponse close(@RequestHeader(value = RankingHeaders.USER_ROLE, required = false) String role) {
        admin.validate(role);
        return seasons.close();
    }
}
