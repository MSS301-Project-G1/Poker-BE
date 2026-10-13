package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.constant.RankingHeaders;
import com.msspoker.rankingservice.dto.response.MyRankingResponse;
import com.msspoker.rankingservice.dto.response.PageResponse;
import com.msspoker.rankingservice.dto.response.RankedPlayerResponse;
import com.msspoker.rankingservice.service.RankingService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ranking")
public class RankingController {
    private final RankingService ranking;

    @GetMapping("/leaderboard")
    public PageResponse<RankedPlayerResponse> leaderboard(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(RankingConstants.MAX_PAGE_SIZE) int size) {
        return ranking.leaderboard(page, size);
    }

    @GetMapping("/me")
    public MyRankingResponse me(@RequestHeader(RankingHeaders.USER_ID) UUID accountId) {
        return ranking.me(accountId);
    }
}
