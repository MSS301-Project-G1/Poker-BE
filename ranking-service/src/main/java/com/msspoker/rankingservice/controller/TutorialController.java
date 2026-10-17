package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.constant.RankingHeaders;
import com.msspoker.rankingservice.dto.response.TutorialResponse;
import com.msspoker.rankingservice.service.TutorialService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tutorial")
public class TutorialController {
    private final TutorialService tutorials;

    @GetMapping("/me")
    public TutorialResponse me(@RequestHeader(RankingHeaders.USER_ID) UUID accountId) {
        return tutorials.status(accountId);
    }

    @PostMapping("/complete")
    public TutorialResponse complete(@RequestHeader(RankingHeaders.USER_ID) UUID accountId) {
        return tutorials.complete(accountId);
    }
}
