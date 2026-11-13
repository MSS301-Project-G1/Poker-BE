package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.constant.RankingHeaders;
import com.msspoker.rankingservice.dto.request.AdjustEloRequest;
import com.msspoker.rankingservice.dto.request.CreateSeasonRequest;
import com.msspoker.rankingservice.dto.request.UpdateSeasonRewardsRequest;
import com.msspoker.rankingservice.dto.response.EloAdjustmentResponse;
import com.msspoker.rankingservice.dto.response.MyRankingResponse;
import com.msspoker.rankingservice.dto.response.PageResponse;
import com.msspoker.rankingservice.dto.response.SeasonDetailResponse;
import com.msspoker.rankingservice.service.EloAdjustmentService;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.validator.AdminAccessValidator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/seasons")
public class AdminSeasonController {
    private final SeasonService seasons;
    private final EloAdjustmentService eloAdjustments;
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

    /** Sets a player's active-season Elo; X-User-Id is the admin recorded in the Elo history. */
    @PutMapping("/players/{accountId}/elo")
    public MyRankingResponse adjustElo(@PathVariable UUID accountId,
            @RequestHeader(value = RankingHeaders.USER_ROLE, required = false) String role,
            @RequestHeader(RankingHeaders.USER_ID) UUID adminId, @Valid @RequestBody AdjustEloRequest request) {
        admin.validate(role);
        return eloAdjustments.adjust(adminId, accountId, request);
    }

    @GetMapping("/players/{accountId}/elo-adjustments")
    public PageResponse<EloAdjustmentResponse> eloAdjustments(@PathVariable UUID accountId,
            @RequestHeader(value = RankingHeaders.USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(RankingConstants.MAX_PAGE_SIZE) int size) {
        admin.validate(role);
        return eloAdjustments.history(accountId, page, size);
    }
}
