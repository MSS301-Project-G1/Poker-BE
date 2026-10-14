package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.constant.RankingHeaders;
import com.msspoker.rankingservice.dto.response.SeasonResponse;
import com.msspoker.rankingservice.dto.response.SeasonRewardResponse;
import com.msspoker.rankingservice.dto.response.TierRewardResponse;
import com.msspoker.rankingservice.service.SeasonRewardService;
import com.msspoker.rankingservice.service.SeasonService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seasons")
public class SeasonController {
    private final SeasonService seasons;
    private final SeasonRewardService rewards;

    @GetMapping("/current")
    public SeasonResponse current() {
        return seasons.current();
    }

    @GetMapping("/{seasonId}/rewards")
    public List<TierRewardResponse> tierRewards(@PathVariable UUID seasonId) {
        return seasons.rewards(seasonId);
    }

    @GetMapping("/rewards/me")
    public List<SeasonRewardResponse> myRewards(@RequestHeader(RankingHeaders.USER_ID) UUID accountId) {
        return rewards.mine(accountId);
    }

    @PostMapping("/{seasonId}/rewards/claim")
    public SeasonRewardResponse claim(@PathVariable UUID seasonId, @RequestHeader(RankingHeaders.USER_ID) UUID accountId) {
        return rewards.claim(seasonId, accountId);
    }
}
