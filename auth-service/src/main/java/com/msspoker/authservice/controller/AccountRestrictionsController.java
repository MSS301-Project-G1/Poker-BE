package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.auth.response.AccountRestrictionsResponse;
import com.msspoker.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Hidden
@RestController
@RequiredArgsConstructor
public class AccountRestrictionsController {
    private final AuthService authService;

    // Internal read contract: raw JSON, no cookies or envelope; the gateway blocks /internal/**.
    @GetMapping("/internal/accounts/{accountId}/restrictions")
    public ResponseEntity<AccountRestrictionsResponse> restrictions(@PathVariable UUID accountId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.restrictions(accountId));
    }
}
