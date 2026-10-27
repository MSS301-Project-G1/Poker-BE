package com.msspoker.shopservice.scheduler;

import com.msspoker.shopservice.entity.AssetGenerationBatch;
import com.msspoker.shopservice.repository.AssetGenerationBatchRepository;
import com.msspoker.shopservice.service.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratedAssetCleanupSchedulerTest {

    @Mock
    private AssetGenerationBatchRepository batchRepository;

    @Mock
    private S3StorageService storageService;

    private GeneratedAssetCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new GeneratedAssetCleanupScheduler(batchRepository, storageService);
    }

    @Test
    @DisplayName("cleanupStaleGeneratedBatches: xóa thư mục S3 và cập nhật status=EXPIRED cho batch quá hạn")
    void cleanupStaleGeneratedBatches_WhenExpired_DeletesS3AndMarksExpired() {
        UUID batchId = UUID.randomUUID();
        AssetGenerationBatch batch = AssetGenerationBatch.builder()
                .id(batchId)
                .categoryCode("CARD_SKIN")
                .status("GENERATED")
                .expiresAt(Instant.now().minusSeconds(100))
                .build();

        when(batchRepository.findByStatusAndExpiresAtLessThanEqual(eq("GENERATED"), any(Instant.class)))
                .thenReturn(List.of(batch));

        scheduler.cleanupStaleGeneratedBatches();

        verify(storageService).deleteFolder("generated/" + batchId + "/");
        assertThat(batch.getStatus()).isEqualTo("EXPIRED");
        verify(batchRepository).save(batch);
    }

    @Test
    @DisplayName("cleanupStaleGeneratedBatches: không làm gì nếu không có batch nào quá hạn")
    void cleanupStaleGeneratedBatches_WhenNone_DoesNothing() {
        when(batchRepository.findByStatusAndExpiresAtLessThanEqual(eq("GENERATED"), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.cleanupStaleGeneratedBatches();

        verify(storageService, never()).deleteFolder(any());
        verify(batchRepository, never()).save(any());
    }
}
