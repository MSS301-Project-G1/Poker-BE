package com.msspoker.authservice.controller;

import com.msspoker.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Hidden
@RestController
@RequiredArgsConstructor
public class InternalAdminAccountsController {
    private final AuthService authService;

    // Internal read contract like restrictions: a raw array of ADMIN account ids, so ranking can leave staff off
    // the leaderboards. Deleted accounts are not listed; the gateway blocks /internal/**.
    @GetMapping("/internal/accounts/admins")
    public ResponseEntity<List<UUID>> admins() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.adminIds());
    }
}
