package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.dto.response.SocialDtos.*;
import com.msspoker.socialservice.service.SocialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendsController {
  private final SocialService social;

  public record SendRequest(@NotNull UUID addresseeId) {}

  @GetMapping
  public PageView<UUID> friends(
      @RequestHeader("X-User-Id") UUID actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return social.friends(actor, page, size);
  }

  @GetMapping("/requests")
  public PageView<FriendRequestView> requests(
      @RequestHeader("X-User-Id") UUID actor,
      @RequestParam(defaultValue = "INCOMING") String direction,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return social.requests(actor, direction, page, size);
  }

  @PostMapping("/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public FriendRequestView send(
      @RequestHeader("X-User-Id") UUID actor, @Valid @RequestBody SendRequest r) {
    return social.send(actor, r.addresseeId());
  }

  @PostMapping("/requests/{id}/accept")
  public FriendRequestView accept(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) {
    return social.respond(actor, id, "ACCEPTED");
  }

  @PostMapping("/requests/{id}/decline")
  public FriendRequestView decline(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) {
    return social.respond(actor, id, "DECLINED");
  }

  @DeleteMapping("/requests/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) {
    social.respond(actor, id, "CANCELLED");
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) {
    social.remove(actor, id);
  }
}
