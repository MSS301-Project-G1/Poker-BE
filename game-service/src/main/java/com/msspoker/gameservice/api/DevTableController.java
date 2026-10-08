package com.msspoker.gameservice.api;

import com.msspoker.gameservice.service.DevTableService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev")
@RequiredArgsConstructor
@RequestMapping("/dev")
public class DevTableController {
    private final DevTableService tables;

    @PostMapping("/tables")
    public DevTableService.DevTable create(@RequestParam(defaultValue = "1") @Min(1) @Max(6) int humans,
            @RequestParam(defaultValue = "5") @Min(0) @Max(5) int bots) {
        return tables.create(humans, bots);
    }
}
