package com.msspoker.shopservice.scheduler;

import com.msspoker.shopservice.entity.AssetGenerationBatch;
import com.msspoker.shopservice.repository.AssetGenerationBatchRepository;
import com.msspoker.shopservice.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduler tự động dọn dẹp các Batch tài nguyên đồ họa do AI sinh ra nhưng Admin không bấm áp dụng quá 24h.
 * - Giải phóng bộ nhớ MinIO / S3.
 * - Cập nhật trạng thái batch thành EXPIRED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneratedAssetCleanupScheduler {

    private final AssetGenerationBatchRepository batchRepository;
    private final S3StorageService storageService;

    @Scheduled(
            fixedDelayString = "${shop.asset.cleanup-delay-ms:3600000}",
            initialDelayString = "${shop.asset.cleanup-initial-delay-ms:60000}"
    )
    @Transactional
    public void cleanupStaleGeneratedBatches() {
        Instant now = Instant.now();
        List<AssetGenerationBatch> expiredBatches = batchRepository.findByStatusAndExpiresAtLessThanEqual("GENERATED", now);

        if (expiredBatches.isEmpty()) {
            return;
        }

        log.info("Tìm thấy {} batch tài nguyên AI chưa áp dụng đã quá hạn 24h. Tiến hành thu hồi S3 storage...", expiredBatches.size());

        for (AssetGenerationBatch batch : expiredBatches) {
            try {
                storageService.deleteFolder("generated/" + batch.getId() + "/");
                batch.setStatus("EXPIRED");
                batchRepository.save(batch);
                log.info("Đã dọn dẹp và thu hồi batchId={} (category={})", batch.getId(), batch.getCategoryCode());
            } catch (Exception e) {
                log.error("Lỗi khi dọn dẹp batchId={}: {}", batch.getId(), e.getMessage());
            }
        }
    }
}
