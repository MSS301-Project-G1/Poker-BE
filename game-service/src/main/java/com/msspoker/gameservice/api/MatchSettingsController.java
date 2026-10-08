package com.msspoker.gameservice.api;

import com.msspoker.gameservice.engine.GameMode;
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
    public List<GameDtos.MatchSettings> list() {
        return settings.list();
    }

    @PutMapping("/{mode}")
    public GameDtos.MatchSettings update(@PathVariable GameMode mode, @Valid @RequestBody GameDtos.UpdateSettings request) {
        return settings.update(mode, request);
    }
}
