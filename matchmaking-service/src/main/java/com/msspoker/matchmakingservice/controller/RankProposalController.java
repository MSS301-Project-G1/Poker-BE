package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.service.NormalProposalService;
import com.msspoker.matchmakingservice.service.RankProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API phản hồi đề xuất trận Rank; service kiểm tra thành viên, deadline và tiến trình tạo bàn. */
@RestController
@RequestMapping("/api/matchmaking/proposals/{proposalId}")
@Tag(name = "Rank proposals", description = "Chấp nhận hoặc từ chối proposal Normal/Rank trong deadline server.")
public class RankProposalController {
    private final RankProposalService proposals;
    private final NormalProposalService normal;

    public RankProposalController(RankProposalService proposals, NormalProposalService normal) {
        this.proposals = proposals;
        this.normal = normal;
    }

    /** Ghi nhận đồng ý; service điều phối bước tiếp theo khi đủ người chấp nhận. */
    @PostMapping("/accept")
    @Operation(operationId = "acceptRankProposal", summary = "Accept proposal Normal/Rank")
    public QueueStatusResponse accept(
            @PathVariable UUID proposalId,
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        return normal.handles(proposalId) ? normal.accept(proposalId, accountId) : proposals.accept(proposalId, accountId);
    }

    /** Từ chối đề xuất; service xử lý hủy và cập nhật trạng thái người liên quan. */
    @PostMapping("/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "declineRankProposal", summary = "Decline proposal Normal/Rank")
    public void decline(
            @PathVariable UUID proposalId,
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        if (normal.handles(proposalId)) normal.decline(proposalId, accountId);
        else proposals.decline(proposalId, accountId);
    }
}
