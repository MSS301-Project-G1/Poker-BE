package com.msspoker.shopservice.datainit;

import com.msspoker.shopservice.client.AuthClient;
import com.msspoker.shopservice.config.DataInitProperties;
import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccountGrantJobTest {

    private static final String EMAIL = "user@poker.com";

    @Mock
    private AuthClient authClient;

    @Mock
    private InventoryService inventoryService;

    private DemoAccountGrantJob job(String... emails) {
        return new DemoAccountGrantJob(new DataInitProperties("https://bucket.test", "http://auth.test",
                Arrays.asList(emails)), authClient, inventoryService);
    }

    @Test
    @DisplayName("Tìm được tài khoản demo: cấp mọi sản phẩm seed với key cố định, lần sau không gọi lại")
    void grantsSeedProductsOnceResolved() {
        UUID accountId = UUID.randomUUID();
        when(authClient.findDemoAccountId(EMAIL)).thenReturn(Optional.of(accountId));
        DemoAccountGrantJob job = job(EMAIL);

        job.grantPending();
        job.grantPending();

        verify(authClient, times(1)).findDemoAccountId(EMAIL);
        verify(inventoryService).grantItem(new GrantItemRequest(accountId, SeedProducts.MOCK_DECK_ID,
                "data-init:" + SeedProducts.MOCK_DECK_ID + ":" + accountId));
        verify(inventoryService).grantItem(new GrantItemRequest(accountId, SeedProducts.TAUNT_SOUND_ID,
                "data-init:" + SeedProducts.TAUNT_SOUND_ID + ":" + accountId));
    }

    @Test
    @DisplayName("auth-service chưa sẵn sàng: giữ email lại và thử ở lần chạy sau")
    void retriesUntilAccountExists() {
        UUID accountId = UUID.randomUUID();
        when(authClient.findDemoAccountId(EMAIL)).thenReturn(Optional.empty(), Optional.of(accountId));
        DemoAccountGrantJob job = job(EMAIL);

        job.grantPending();
        verify(inventoryService, never()).grantItem(any());

        job.grantPending();
        verify(inventoryService, times(SeedProducts.ALL_IDS.size())).grantItem(any());
    }

    @Test
    @DisplayName("Sản phẩm seed chưa được tạo: thử lại thay vì bỏ qua tài khoản")
    void retriesWhenSeedProductsAreNotReady() {
        UUID accountId = UUID.randomUUID();
        when(authClient.findDemoAccountId(EMAIL)).thenReturn(Optional.of(accountId));
        when(inventoryService.grantItem(any())).thenThrow(ProductExceptions.productNotFound()).thenReturn(null);
        DemoAccountGrantJob job = job(EMAIL);

        job.grantPending();
        job.grantPending();

        verify(authClient, times(2)).findDemoAccountId(EMAIL);
    }

    @Test
    @DisplayName("Hết số lần thử: dừng gọi auth-service")
    void stopsAfterMaxAttempts() {
        when(authClient.findDemoAccountId(EMAIL)).thenReturn(Optional.empty());
        DemoAccountGrantJob job = job(EMAIL);

        for (int i = 0; i < DemoAccountGrantJob.MAX_ATTEMPTS + 5; i++) {
            job.grantPending();
        }

        verify(authClient, times(DemoAccountGrantJob.MAX_ATTEMPTS)).findDemoAccountId(EMAIL);
    }

    @Test
    @DisplayName("Không cấu hình email demo (DATA_INIT_USER_EMAIL trống): không làm gì")
    void doesNothingWithoutEmails() {
        job(" ").grantPending();
        new DemoAccountGrantJob(new DataInitProperties("https://bucket.test", "http://auth.test", List.of()),
                authClient, inventoryService).grantPending();

        verifyNoInteractions(authClient, inventoryService);
    }
}
