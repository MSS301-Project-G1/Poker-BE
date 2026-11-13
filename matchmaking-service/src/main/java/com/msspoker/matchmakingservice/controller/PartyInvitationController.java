package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.dto.request.PartyInviteRequest;
import com.msspoker.matchmakingservice.dto.response.PartyInvitationResponse;
import com.msspoker.matchmakingservice.dto.response.PartyResponse;
import com.msspoker.matchmakingservice.service.PartyInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/party/invitations")
@RequiredArgsConstructor
@Tag(name = "Party - Lời mời", description = "Mời bạn bè vào party Normal. Chỉ chủ nhóm được mời; lời mời hết hạn sau 5 phút. Danh tính lấy từ Gateway.")
public class PartyInvitationController {
    private final PartyInvitationService invitations;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Mời bạn vào party", description = "Tạo party nếu chưa có. Body accountId là bạn được mời; kiểm tra bạn bè/chặn, sức chứa và trạng thái trận. Gửi thông báo qua Social; không trả mật khẩu.")
    public PartyInvitationResponse invite(@RequestHeader("X-User-Id") UUID actor, @Valid @RequestBody PartyInviteRequest body) {
        return invitations.invite(actor, body.accountId());
    }

    @GetMapping
    @Operation(summary = "Danh sách lời mời party", description = "INCOMING hoặc OUTGOING; trả tối đa 50 lời mời mới nhất của chính người gọi, gồm trạng thái và thời điểm hết hạn.")
    public List<PartyInvitationResponse> list(@RequestHeader("X-User-Id") UUID actor,
            @RequestParam(defaultValue = "INCOMING") String direction) { return invitations.list(actor, direction); }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Chấp nhận và vào party", description = "Chỉ người nhận được chấp nhận. Kiểm tra lại bạn bè, hết hạn, chủ nhóm, sức chứa, mật khẩu đã đổi và trạng thái trận; không cần nhập mã/mật khẩu.")
    public PartyResponse accept(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) { return invitations.accept(actor, id); }

    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Từ chối lời mời party")
    public void decline(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) { invitations.decline(actor, id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hủy lời mời party đã gửi")
    public void cancel(@RequestHeader("X-User-Id") UUID actor, @PathVariable UUID id) { invitations.cancel(actor, id); }
}
