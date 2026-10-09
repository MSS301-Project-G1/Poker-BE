package com.msspoker.gameservice.controller;

import com.msspoker.gameservice.dto.request.UpdateMatchSettingsRequest;
import com.msspoker.gameservice.dto.response.MatchSettingsResponse;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.service.SettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/match-settings")
public class MatchSettingsController {
    private final SettingsService settings;

    @GetMapping
    public List<MatchSettingsResponse> list() {
        return settings.list();
    }

    @PutMapping("/{mode}")
    public MatchSettingsResponse update(@PathVariable GameMode mode, @Valid @RequestBody UpdateMatchSettingsRequest request) {
        return settings.update(mode, request);
    }
}
