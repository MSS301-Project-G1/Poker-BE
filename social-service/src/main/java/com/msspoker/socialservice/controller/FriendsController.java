package com.msspoker.socialservice.controller;

import com.msspoker.socialservice.constant.SocialHeaders;
import com.msspoker.socialservice.dto.request.SendFriendRequest;
import com.msspoker.socialservice.dto.response.FriendRequestResponse;
import com.msspoker.socialservice.dto.response.PageResponse;
import com.msspoker.socialservice.service.SocialService;

import jakarta.validation.Valid;

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

import java.util.UUID;

/** API bạn bè; actor lấy từ header tin cậy sau bước kiểm tra ingress key. */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendsController {
    private final SocialService social;

    /** Lấy UUID bạn bè theo trang; không đọc hồ sơ trong database Auth. */
    @GetMapping
    public PageResponse<UUID> friends(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return social.friends(actor, page, size);
    }

    /** Lấy lời mời theo chiều INCOMING hoặc OUTGOING, có phân trang. */
    @GetMapping("/requests")
    public PageResponse<FriendRequestResponse> requests(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor,
            @RequestParam(defaultValue = "INCOMING") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return social.requests(actor, direction, page, size);
    }

    /** Body chỉ định người nhận; người gửi luôn được xác định từ header. */
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse send(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor,
            @Valid @RequestBody SendFriendRequest r) {
        return social.send(actor, r.addresseeId());
    }

    /** id là mã lời mời; service kiểm tra người nhận rồi tạo quan hệ bạn bè. */
    @PostMapping("/requests/{id}/accept")
    public FriendRequestResponse accept(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        return social.respond(actor, id, "ACCEPTED");
    }

    /** id là mã lời mời; chỉ người nhận được từ chối. */
    @PostMapping("/requests/{id}/decline")
    public FriendRequestResponse decline(
            @RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        return social.respond(actor, id, "DECLINED");
    }

    /** Người gửi hủy lời mời của mình; service kiểm tra quyền và trạng thái. */
    @DeleteMapping("/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        social.respond(actor, id, "CANCELLED");
    }

    /** id là UUID người bạn cần gỡ; quan hệ bạn bè được xóa hai chiều. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader(SocialHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        social.remove(actor, id);
    }
}
