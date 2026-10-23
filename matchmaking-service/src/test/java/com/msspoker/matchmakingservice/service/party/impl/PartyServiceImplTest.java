package com.msspoker.matchmakingservice.service.party.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.service.store.PartyStore;
import com.msspoker.matchmakingservice.service.store.QueueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    private static final String CODE = "AB12CD34";
    private static final String PASSWORD = "123456";

    @Mock private PartyStore store;
    @Mock private QueueStore queueStore;
    private PartyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PartyServiceImpl(store, new MatchmakingProperties(), Clock.fixed(NOW, ZoneOffset.UTC), queueStore);
    }

    @Test
    void createGeneratesSixDigitPasswordAndReturnsIt() {
        UUID leader = UUID.randomUUID();
        when(store.create(any(), anyString(), anyString(), eq(leader), eq(NOW)))
                .thenReturn(PartyStore.CreateResult.CREATED);

        var response = service.create(leader);

        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(store).create(any(), anyString(), password.capture(), eq(leader), eq(NOW));
        assertThat(password.getValue()).matches("\\d{6}");
        assertThat(response.password()).isEqualTo(password.getValue());
    }

    @Test
    void wrongPasswordIsRejectedWithLocalizedError() {
        UUID leader = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        when(store.findByCode(CODE)).thenReturn(Optional.of(party(leader)));
        when(store.join(joiner, CODE, "000000", 6, NOW)).thenReturn(PartyStore.JoinResult.WRONG_PASSWORD);

        assertThatThrownBy(() -> service.join(joiner, CODE, "000000"))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_PARTY_WRONG_PASSWORD);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getMessage()).isEqualTo("Mật khẩu party không đúng.");
                });
    }

    @Test
    void lowercaseCodeJoinsWithCorrectPassword() {
        UUID leader = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        when(store.findByCode(CODE)).thenReturn(Optional.of(party(leader)));
        when(store.join(joiner, CODE, PASSWORD, 6, NOW)).thenReturn(PartyStore.JoinResult.JOINED);
        when(store.findByAccountId(joiner)).thenReturn(Optional.of(party(leader, joiner)));

        var response = service.join(joiner, CODE.toLowerCase(), PASSWORD);

        assertThat(response.memberIds()).containsExactly(leader, joiner);
    }

    @Test
    void kickRotatesPasswordSoKickedPlayerCannotRejoin() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        when(store.findByAccountId(leader)).thenReturn(Optional.of(party(leader, member)));
        when(store.kick(eq(leader), eq(member), anyString())).thenReturn(PartyStore.KickResult.KICKED);

        service.kick(leader, member);

        ArgumentCaptor<String> newPassword = ArgumentCaptor.forClass(String.class);
        verify(store).kick(eq(leader), eq(member), newPassword.capture());
        assertThat(newPassword.getValue()).matches("\\d{6}");
    }

    private static PartySnapshot party(UUID... members) {
        return new PartySnapshot(UUID.randomUUID(), CODE, PASSWORD, members[0], List.of(members), NOW);
    }
}
