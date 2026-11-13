package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.dto.response.FriendshipResponse;
import com.msspoker.socialservice.service.SocialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** API giữa các service; filter kiểm tra X-Social-Internal-Key, không công khai qua Gateway. */
@RestController
@RequestMapping("/internal/friends")
@RequiredArgsConstructor
@Tag(name = "Social - Nội bộ", description = "API giữa các backend service; yêu cầu header X-Social-Internal-Key, không công khai qua Gateway.")
public class InternalFriendsController {
    private final SocialService social;

    /** Trả UUID bạn bè của tài khoản được chỉ định cho service gọi nội bộ. */
    @Operation(summary = "Danh sách bạn bè cho service nội bộ", description = "id là UUID tài khoản cần tra cứu; trả danh sách UUID bạn bè cho backend gọi nội bộ.")
    @GetMapping("/{id}")
    public List<UUID> friends(@PathVariable UUID id) {
        return social.internalFriends(id);
    }

    /** Trả trạng thái bạn bè và chặn giữa hai tài khoản để Chat kiểm tra quyền liên lạc. */
    @Operation(summary = "Kiểm tra quan hệ giữa hai tài khoản", description = "a và b là UUID hai tài khoản. Trả trạng thái bạn bè và chặn để Chat kiểm tra quyền liên lạc.")
    @GetMapping("/{a}/with/{b}")
    public FriendshipResponse relation(@PathVariable UUID a, @PathVariable UUID b) {
        return social.relation(a, b);
    }
}
