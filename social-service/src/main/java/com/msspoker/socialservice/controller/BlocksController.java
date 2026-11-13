package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.constant.SocialHeaders;
import com.msspoker.socialservice.dto.response.PageResponse;
import com.msspoker.socialservice.service.SocialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API chặn tài khoản; actor là người thao tác, id là tài khoản mục tiêu. */
@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
@Tag(name = "Social - Chặn tài khoản", description = "Quản lý danh sách chặn của người đang đăng nhập; API công khai gọi qua Gateway.")
public class BlocksController {
    private final SocialService social;

    /** Lấy danh sách tài khoản do actor chặn, có phân trang. */
    @Operation(summary = "Danh sách tài khoản tôi đã chặn", description = "Trả UUID tài khoản bị chặn, có phân trang; page bắt đầu từ 0, size mặc định 20.")
    @GetMapping
    public PageResponse<UUID> list(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return social.blocks(actor, page, size);
    }

    /** Chặn tài khoản, đồng thời xử lý lời mời đang chờ và quan hệ bạn bè ở service. */
    @Operation(summary = "Chặn tài khoản", description = "id là UUID tài khoản mục tiêu. Đồng thời xử lý lời mời đang chờ và quan hệ bạn bè; thành công trả HTTP 204.")
    @PostMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        social.block(actor, id);
    }

    /** Gỡ lượt chặn do actor tạo; không tự khôi phục tình bạn. */
    @Operation(summary = "Bỏ chặn tài khoản", description = "Chỉ gỡ lượt chặn do người gọi tạo, không tự khôi phục tình bạn. Thành công trả HTTP 204.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        social.unblock(actor, id);
    }
}
