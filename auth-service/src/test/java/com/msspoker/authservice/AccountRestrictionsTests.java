package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.service.AccountRestrictionsService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.security.InternalServiceKeyFilter;
import com.msspoker.authservice.shared.infrastructure.security.SecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountRestrictionsTests {
    @Test void exposesStoredRestrictionsAndRejectsUnknownAccount() {
        var accounts = mock(AccountRepository.class);
        var id = UUID.randomUUID();
        var account = new Account();
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setEmailVerified(true);
        account.setRankBannedUntil(Instant.now().plusSeconds(60));
        when(accounts.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(account));
        var service = new AccountRestrictionsService(accounts);
        assertFalse(service.restrictions(id).banned());
        assertEquals(account.getRankBannedUntil(), service.restrictions(id).rankBannedUntil());
        account.setBanned(true);
        assertTrue(service.restrictions(id).banned());
        account.setBanned(false);
        account.setEmailVerified(false);
        assertTrue(service.restrictions(id).banned());
        assertThrows(ApiException.class, () -> service.restrictions(UUID.randomUUID()));
    }

    @Test void internalEndpointRequiresMatchingNonemptyServiceKey() throws Exception {
        var errors = mock(SecurityErrorHandler.class);
        var filter = new InternalServiceKeyFilter("test-key", errors);
        var request = new MockHttpServletRequest();
        request.setServletPath("/internal/accounts/" + UUID.randomUUID() + "/restrictions");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(errors).write(request, response, ErrorCode.AUTH_FORBIDDEN);
        verifyNoInteractions(chain);
        request.addHeader("X-Game-Service-Key", "test-key");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
