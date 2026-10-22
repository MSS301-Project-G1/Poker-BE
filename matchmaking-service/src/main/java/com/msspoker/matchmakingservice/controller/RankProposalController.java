package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.service.RankProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API phản hồi đề xuất trận Rank; service kiểm tra thành viên, deadline và tiến trình tạo bàn. */
@RestController
@RequestMapping("/api/matchmaking/proposals/{proposalId}")
@Tag(name = "Rank proposals", description = "Chấp nhận hoặc từ chối proposal Rank trong deadline server.")
public class RankProposalController {
    private final RankProposalService proposals;

    public RankProposalController(RankProposalService proposals) {
        this.proposals = proposals;
    }

    /** Ghi nhận đồng ý; service điều phối bước tiếp theo khi đủ người chấp nhận. */
    @PostMapping("/accept")
    @Operation(operationId = "acceptRankProposal", summary = "Accept proposal Rank")
    public QueueStatusResponse accept(
            @PathVariable UUID proposalId,
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        return proposals.accept(proposalId, accountId);
    }

    /** Từ chối đề xuất; service xử lý hủy và cập nhật trạng thái người liên quan. */
    @PostMapping("/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "declineRankProposal", summary = "Decline proposal Rank")
    public void decline(
            @PathVariable UUID proposalId,
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        proposals.decline(proposalId, accountId);
    }
}
