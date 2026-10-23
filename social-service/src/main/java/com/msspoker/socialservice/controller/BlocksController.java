package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.constant.SocialHeaders;
import com.msspoker.socialservice.dto.response.PageResponse;
import com.msspoker.socialservice.service.SocialService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** API chặn tài khoản; actor là người thao tác, id là tài khoản mục tiêu. */
@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlocksController {
  private final SocialService social;

  /** Lấy danh sách tài khoản do actor chặn, có phân trang. */
  @GetMapping
  public PageResponse<UUID> list(
      @RequestHeader(SocialHeaders.USER_ID) UUID actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return social.blocks(actor, page, size);
  }

  /** Chặn tài khoản, đồng thời xử lý lời mời đang chờ và quan hệ bạn bè ở service. */
  @PostMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void block(@RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
    social.block(actor, id);
  }

  /** Gỡ lượt chặn do actor tạo; không tự khôi phục tình bạn. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unblock(@RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
    social.unblock(actor, id);
  }
}
