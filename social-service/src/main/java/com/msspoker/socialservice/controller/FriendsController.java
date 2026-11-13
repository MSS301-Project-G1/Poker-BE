package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.constant.SocialHeaders;
import com.msspoker.socialservice.dto.request.SendFriendRequest;
import com.msspoker.socialservice.dto.response.FriendRequestResponse;
import com.msspoker.socialservice.dto.response.PageResponse;
import com.msspoker.socialservice.service.SocialService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** API bạn bè; actor lấy từ header tin cậy sau bước kiểm tra ingress key. */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Tag(name = "Social - Bạn bè", description = "Quản lý bạn bè và lời mời của người đang đăng nhập; API công khai gọi qua Gateway.")
public class FriendsController {
    private final SocialService social;

    /** Lấy UUID bạn bè theo trang; không đọc hồ sơ trong database Auth. */
    @Operation(summary = "Danh sách bạn bè của tôi", description = "Trả UUID tài khoản bạn bè, có phân trang; page bắt đầu từ 0, size mặc định 20. Thông tin hồ sơ lấy từ Auth.")
    @GetMapping
    public PageResponse<UUID> friends(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return social.friends(actor, page, size);
    }

    /** Lọc ra những tài khoản đã là bạn trong danh sách gửi lên; không tiết lộ trạng thái chặn. */
    @Operation(summary = "Kiểm tra những ai đã là bạn", description = "ids là 1–100 UUID tài khoản, cách nhau bởi dấu phẩy. Trả về các UUID trong số đó đã là bạn; không cho biết ai đã chặn ai.")
    @GetMapping("/check")
    public List<UUID> check(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor, @RequestParam List<UUID> ids) {
        return social.friendsAmong(actor, ids);
    }

    /** Lấy lời mời theo chiều INCOMING hoặc OUTGOING, có phân trang. */
    @Operation(summary = "Danh sách lời mời kết bạn", description = "direction là INCOMING (nhận được, mặc định) hoặc OUTGOING (đã gửi). page bắt đầu từ 0, size mặc định 20.")
    @GetMapping("/requests")
    public PageResponse<FriendRequestResponse> requests(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor,
            @RequestParam(defaultValue = "INCOMING") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return social.requests(actor, direction, page, size);
    }

    /** Body chỉ định người nhận; người gửi luôn được xác định từ header. */
    @Operation(summary = "Gửi lời mời kết bạn", description = "Body chứa addresseeId của người nhận; người gửi được xác định từ phiên đăng nhập. Thành công trả HTTP 201.")
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse send(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor,
            @Valid @RequestBody SendFriendRequest r) {
        return social.send(actor, r.addresseeId());
    }

    /** id là mã lời mời; service kiểm tra người nhận rồi tạo quan hệ bạn bè. */
    @Operation(summary = "Chấp nhận lời mời kết bạn", description = "id là UUID lời mời, không phải UUID tài khoản. Chỉ người nhận được chấp nhận; tạo quan hệ bạn bè hai chiều.")
    @PostMapping("/requests/{id}/accept")
    public FriendRequestResponse accept(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        return social.respond(actor, id, "ACCEPTED");
    }

    /** id là mã lời mời; chỉ người nhận được từ chối. */
    @Operation(summary = "Từ chối lời mời kết bạn", description = "id là UUID lời mời; chỉ người nhận được từ chối lời mời đó.")
    @PostMapping("/requests/{id}/decline")
    public FriendRequestResponse decline(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        return social.respond(actor, id, "DECLINED");
    }

    /** Người gửi hủy lời mời của mình; service kiểm tra quyền và trạng thái. */
    @Operation(summary = "Hủy lời mời kết bạn đã gửi", description = "id là UUID lời mời; chỉ người gửi được hủy lời mời của mình. Thành công trả HTTP 204.")
    @DeleteMapping("/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        social.respond(actor, id, "CANCELLED");
    }

    /** id là UUID người bạn cần gỡ; quan hệ bạn bè được xóa hai chiều. */
    @Operation(summary = "Hủy kết bạn", description = "id là UUID tài khoản người bạn cần gỡ. Xóa quan hệ bạn bè hai chiều; thành công trả HTTP 204.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        social.remove(actor, id);
    }
}
