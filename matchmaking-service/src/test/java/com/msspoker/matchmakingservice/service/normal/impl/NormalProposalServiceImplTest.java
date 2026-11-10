package com.msspoker.matchmakingservice.service.normal.impl;

import com.msspoker.matchmakingservice.service.normal.NormalMatchCoordinator;
import com.msspoker.matchmakingservice.service.normal.NormalMatchmakingService;
import com.msspoker.matchmakingservice.service.store.PartyStore;
import com.msspoker.matchmakingservice.service.store.QueueStore;

import com.msspoker.matchmakingservice.entity.*;
import com.msspoker.matchmakingservice.enums.*;
import com.msspoker.matchmakingservice.repository.MatchProposalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class NormalProposalServiceImplTest {
    final UUID id = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-24T00:00:00Z");
    final MatchProposalRepository repo = mock(MatchProposalRepository.class);
    final NormalMatchCoordinator coordinator = mock(NormalMatchCoordinator.class);
    final QueueStore queue = mock(QueueStore.class);
    final NormalProposalServiceImpl service = new NormalProposalServiceImpl(repo, coordinator,
            mock(NormalMatchmakingService.class), queue, mock(PartyStore.class), Clock.fixed(now, ZoneOffset.UTC), mock(PlatformTransactionManager.class));

    MatchProposalEntity proposal(Instant deadline) {
        var p = MatchProposalEntity.normalPending(id, now, deadline);
        p.addPlayer(MatchProposalPlayerEntity.normalPending(a, now, now));
        p.addPlayer(MatchProposalPlayerEntity.normalPending(b, now, now));
        when(repo.findLockedById(id)).thenReturn(Optional.of(p));
        return p;
    }
    @Test void waitsForEveryoneAndStartsOnlyOnce() {
        var p = proposal(now.plusSeconds(30));
        service.accept(id, a);
        verifyNoInteractions(coordinator);
        service.accept(id, b);
        service.accept(id, b);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.CREATING_TABLE);
        verify(coordinator, times(1)).retry(eq(id), argThat(entries -> entries.size() == 2));
    }
    @Test void declineReturnsEveryoneWithoutCreatingTableAndIsIdempotent() {
        var p = proposal(now.plusSeconds(30));
        service.accept(id, a);
        service.decline(id, b);
        service.decline(id, b);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.DECLINED);
        verify(queue, times(1)).discard(argThat(entries -> entries.size() == 2));
        verifyNoInteractions(coordinator);
    }
    @Test void lateAcceptanceExpiresAndCleansUpInsteadOfStartingGame() {
        var p = proposal(now);
        service.accept(id, a);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.EXPIRED);
        verify(queue).discard(argThat(entries -> entries.size() == 2));
        verifyNoInteractions(coordinator);
    }
    @Test void outsiderCannotAcceptOrDecline() {
        var p = proposal(now.plusSeconds(30));
        assertThatThrownBy(() -> service.accept(id, UUID.randomUUID())).isInstanceOf(com.msspoker.matchmakingservice.exception.MatchmakingApiException.class);
        assertThatThrownBy(() -> service.decline(id, UUID.randomUUID())).isInstanceOf(com.msspoker.matchmakingservice.exception.MatchmakingApiException.class);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.WAITING_ACCEPTANCE);
        verifyNoInteractions(coordinator, queue);
    }
    @Test void schedulerExpiresUnansweredProposal() {
        var p = proposal(now.minusSeconds(1));
        when(repo.findTop20ByModeAndStatusAndAcceptDeadlineBeforeOrderByAcceptDeadlineAsc(MatchMode.NORMAL, ProposalStatus.WAITING_ACCEPTANCE, now)).thenReturn(List.of(p));
        when(repo.findTop20ByModeAndStatusInAndRedisReconciledAtIsNullOrderByUpdatedAtAsc(eq(MatchMode.NORMAL), any())).thenReturn(List.of(p));
        service.expire();
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.EXPIRED);
        verify(queue).discard(anyList());
        verifyNoInteractions(coordinator);
    }
}
