package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.dto.response.PlayerRankingResponse;
import com.msspoker.rankingservice.dto.response.RankedPlayerResponse;
import com.msspoker.rankingservice.service.RankingService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ranking")
public class InternalRankingController {
    private final RankingService ranking;

    @GetMapping("/{accountId}")
    public PlayerRankingResponse ranking(@PathVariable UUID accountId) {
        return ranking.ranking(accountId);
    }

    @GetMapping("/top")
    public List<RankedPlayerResponse> top(
            @RequestParam(defaultValue = "32") @Min(1) @Max(RankingConstants.MAX_PAGE_SIZE) int limit) {
        return ranking.top(limit);
    }
}
