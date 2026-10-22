package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.dto.response.SocialDtos.PageView;
import com.msspoker.socialservice.service.SocialService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlocksController {
  private final SocialService social;

  @GetMapping
  public PageView<UUID> list(
      @RequestHeader("X-User-Id") UUID actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return social.blocks(actor, page, size);
  }

  @PostMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void block(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) {
    social.block(actor, id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unblock(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) {
    social.unblock(actor, id);
  }
}
