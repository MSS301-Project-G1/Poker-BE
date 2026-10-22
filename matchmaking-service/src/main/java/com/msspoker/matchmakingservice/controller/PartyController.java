package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.dto.request.PartyInviteRequest;
import com.msspoker.matchmakingservice.dto.request.PartyJoinRequest;
import com.msspoker.matchmakingservice.dto.request.PartyKickRequest;
import com.msspoker.matchmakingservice.dto.response.PartyResponse;
import com.msspoker.matchmakingservice.service.PartyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API nhóm chơi Normal; accountId là người thao tác, quyền chủ nhóm được kiểm tra ở service. */
@RestController
@RequestMapping("/api/party")
@Tag(name = "Normal party", description = "Quản lý nhóm bạn chơi chung và ghép cùng một trận Normal.")
public class PartyController {
    private final PartyService party;

    public PartyController(PartyService party) {
        this.party = party;
    }

    /** Lấy party hiện tại; Optional rỗng khi người gọi chưa thuộc nhóm nào. */
    @org.springframework.web.bind.annotation.GetMapping("/me")
    public java.util.Optional<PartyResponse> current(
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId) {
        return party.current(accountId);
    }

    /** Tạo nhóm và đặt người gọi làm chủ party. */
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createParty", summary = "Create party", description = "Tạo party và mã mời; người gọi trở thành chủ party.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo party"),
            @ApiResponse(responseCode = "409", description = "Người chơi đã thuộc một party")
    })
    public PartyResponse create(@Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
                                @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId) {
        return party.create(accountId);
    }

    /** Lấy người được mời từ body rồi giao service kiểm tra và ghi nhận lời mời. */
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "invitePartyMember", summary = "Invite party member")
    public void invite(@Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
                       @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId,
                       @Valid @RequestBody PartyInviteRequest request) {
        party.invite(accountId, request.inviteeId());
    }

    /** Dùng mã party để yêu cầu vào nhóm đã được mời. */
    @PostMapping("/join")
    @Operation(operationId = "joinParty", summary = "Join party", description = "Vào party bằng mã sau khi được chủ party mời.")
    public PartyResponse join(@Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
                              @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId,
                              @Valid @RequestBody PartyJoinRequest request) {
        return party.join(accountId, request.partyCode());
    }

    /** accountId trong header là người thao tác; accountId trong body là người bị mời ra. */
    @PostMapping("/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "kickPartyMember", summary = "Kick party member", description = "Chỉ chủ party được xóa thành viên.")
    public void kick(@Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
                     @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId,
                     @Valid @RequestBody PartyKickRequest request) {
        party.kick(accountId, request.accountId());
    }

    /** Rời nhóm; service xử lý chuyển quyền chủ hoặc giải tán khi cần. */
    @DeleteMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "leaveParty", summary = "Leave party", description = "Rời party; nếu chủ rời thì chuyển quyền cho thành viên vào sớm nhất.")
    public void leave(@Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
                      @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId) {
        party.leave(accountId);
    }
}
