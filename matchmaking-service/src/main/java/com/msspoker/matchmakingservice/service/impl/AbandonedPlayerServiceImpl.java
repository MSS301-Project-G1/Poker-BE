package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.service.AbandonedPlayerService;
import com.msspoker.matchmakingservice.service.store.PartyStore;
import com.msspoker.matchmakingservice.service.store.QueueStore;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AbandonedPlayerServiceImpl implements AbandonedPlayerService {
    private final QueueStore queueStore;
    private final PartyStore partyStore;
    private final MatchProposalPlayerRepository players;

    @Override
    public boolean release(UUID accountId) {
        // Trận đã được đề xuất thì để luồng xác nhận/hết hạn xử lý, như khi người chơi tự bấm rời
        // hàng chờ.
        if (players.existsActiveProposal(accountId)) return false;
        Optional<PartySnapshot> party = partyStore.findByAccountId(accountId);
        Optional<QueueEntry> entry = queueStore.find(accountId);
        if (entry.isPresent() && cancel(entry.get(), party) == QueueStore.CancelResult.RESERVED)
            return false;
        return party.isEmpty()
                || partyStore.leave(accountId) != PartyStore.LeaveResult.PARTY_QUEUED;
    }

    // Party chỉ được ghép khi đủ mọi thành viên, nên một người bỏ đi thì cả nhóm dừng tìm trận.
    private QueueStore.CancelResult cancel(QueueEntry entry, Optional<PartySnapshot> party) {
        if (!entry.belongsToParty()) return queueStore.cancel(entry.accountId(), entry.mode());
        List<QueueEntry> members =
                party.map(
                                snapshot ->
                                        snapshot.memberIds().stream()
                                                .map(queueStore::find)
                                                .flatMap(Optional::stream)
                                                .filter(
                                                        member ->
                                                                entry.partyId()
                                                                        .equals(member.partyId()))
                                                .toList())
                        .orElseGet(() -> List.of(entry));
        return queueStore.cancelParty(members);
    }
}
