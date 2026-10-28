package com.msspoker.shopservice.datainit;

import com.msspoker.shopservice.client.AuthClient;
import com.msspoker.shopservice.config.DataInitProperties;
import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Grants every seed product to the demo accounts in shop.data-init.grant-emails. auth-service owns their
 * ids and may start, or create the accounts, after this service, so pending emails are retried until they
 * resolve or the attempts run out. Grants are idempotent, so restarts never duplicate items.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "shop.data-init.enabled", havingValue = "true")
public class DemoAccountGrantJob {

    static final long RETRY_DELAY_MILLIS = 10_000;
    static final int MAX_ATTEMPTS = 30;

    private final AuthClient authClient;
    private final InventoryService inventoryService;
    // Touched only by the scheduler thread; fixedDelay never overlaps runs.
    private final Set<String> pendingEmails;
    private int attempts;

    public DemoAccountGrantJob(DataInitProperties properties, AuthClient authClient, InventoryService inventoryService) {
        this.authClient = authClient;
        this.inventoryService = inventoryService;
        this.pendingEmails = new LinkedHashSet<>(properties.grantEmails());
    }

    @Scheduled(initialDelay = RETRY_DELAY_MILLIS, fixedDelay = RETRY_DELAY_MILLIS)
    public void grantPending() {
        if (pendingEmails.isEmpty()) {
            return;
        }
        pendingEmails.removeIf(this::grantAll);
        if (!pendingEmails.isEmpty() && ++attempts >= MAX_ATTEMPTS) {
            log.warn("Seed products not granted to {} after {} attempts; is auth-service running with DATA_INIT_ENABLED?",
                    pendingEmails, attempts);
            pendingEmails.clear();
        }
    }

    /** Returns true once the account owns every seed product. */
    private boolean grantAll(String email) {
        Optional<UUID> accountId = authClient.findDemoAccountId(email);
        if (accountId.isEmpty()) {
            return false;
        }
        try {
            for (UUID productId : SeedProducts.ALL_IDS) {
                String grantKey = SeedProducts.GRANT_KEY_PREFIX + productId + ":" + accountId.get();
                inventoryService.grantItem(new GrantItemRequest(accountId.get(), productId, grantKey));
            }
        } catch (AppException ex) {
            // The seed products may not exist until ShopDataInitializer has run.
            log.debug("Seed products not granted to {} yet: {}", email, ex.getMessage());
            return false;
        }
        log.info("Granted seed products to demo account {}", email);
        return true;
    }
}
