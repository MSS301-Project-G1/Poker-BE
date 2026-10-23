package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.dto.response.FriendshipResponse;
import com.msspoker.socialservice.service.SocialService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** API giữa các service; filter kiểm tra X-Social-Internal-Key, không công khai qua Gateway. */
@RestController
@RequestMapping("/internal/friends")
@RequiredArgsConstructor
public class InternalFriendsController {
  private final SocialService social;

  /** Trả UUID bạn bè của tài khoản được chỉ định cho service gọi nội bộ. */
  @GetMapping("/{id}")
  public List<UUID> friends(@PathVariable UUID id) {
    return social.internalFriends(id);
  }

  /** Trả trạng thái bạn bè và chặn giữa hai tài khoản để Chat kiểm tra quyền liên lạc. */
  @GetMapping("/{a}/with/{b}")
  public FriendshipResponse relation(@PathVariable UUID a, @PathVariable UUID b) {
    return social.relation(a, b);
  }
}
