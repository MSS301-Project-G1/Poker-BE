package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.dto.response.SocialDtos.RelationView;
import com.msspoker.socialservice.service.SocialService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/friends")
@RequiredArgsConstructor
public class InternalFriendsController {
  private final SocialService social;

  @GetMapping("/{id}")
  public List<UUID> friends(@PathVariable UUID id) {
    return social.internalFriends(id);
  }

  @GetMapping("/{a}/with/{b}")
  public RelationView relation(@PathVariable UUID a, @PathVariable UUID b) {
    return social.relation(a, b);
  }
}
