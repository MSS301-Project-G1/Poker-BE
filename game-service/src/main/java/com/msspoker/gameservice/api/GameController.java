package com.msspoker.gameservice.api;

import com.msspoker.gameservice.realtime.GameSnapshot;
import com.msspoker.gameservice.security.GameHeaders;
import com.msspoker.gameservice.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/game")
public class GameController {
    private final TableService tables;
    private final GameplayService gameplay;
    private final MatchPersistenceService matches;

    @GetMapping("/me/active-table")
    public Optional<GameDtos.TableCreated> active(@RequestAttribute(GameHeaders.USER_ID) UUID accountId) {
        return tables.active(accountId);
    }

    @GetMapping("/tables/{tableId}")
    public GameSnapshot snapshot(@PathVariable UUID tableId, @RequestAttribute(GameHeaders.USER_ID) UUID accountId) {
        return gameplay.snapshot(tableId, accountId);
    }

    @GetMapping("/tables/{tableId}/result")
    public GameDtos.MatchView result(@PathVariable UUID tableId, @RequestAttribute(GameHeaders.USER_ID) UUID accountId) {
        return matches.result(tableId, accountId);
    }
}
