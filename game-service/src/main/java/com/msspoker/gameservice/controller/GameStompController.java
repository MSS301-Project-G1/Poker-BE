package com.msspoker.gameservice.controller;

import com.msspoker.gameservice.dto.request.ActionRequest;
import com.msspoker.gameservice.dto.request.TauntRequest;
import com.msspoker.gameservice.service.GameplayService;
import com.msspoker.gameservice.service.TauntService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GameStompController {
    private final GameplayService gameplay;
    private final TauntService taunts;

    @MessageMapping("/tables/{tableId}/action")
    public void action(@DestinationVariable UUID tableId, @Valid ActionRequest request, Principal user) {
        gameplay.action(tableId, UUID.fromString(user.getName()), request);
    }

    @MessageMapping("/tables/{tableId}/sync")
    public void sync(@DestinationVariable UUID tableId, Principal user) {
        gameplay.sync(tableId, UUID.fromString(user.getName()));
    }

    @MessageMapping("/tables/{tableId}/taunt")
    public void taunt(@DestinationVariable UUID tableId, @Valid TauntRequest request, Principal user) {
        taunts.taunt(tableId, UUID.fromString(user.getName()), request);
    }
}
