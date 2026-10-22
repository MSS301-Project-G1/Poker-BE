package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.auth.response.AccountRestrictionsResponse;
import com.msspoker.authservice.service.AccountRestrictionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class InternalAccountController {
    private final AccountRestrictionsService restrictions;

    @GetMapping("/{accountId}/restrictions")
    public AccountRestrictionsResponse restrictions(@PathVariable UUID accountId) {
        return restrictions.restrictions(accountId);
    }
}
