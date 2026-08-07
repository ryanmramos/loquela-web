package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundJobIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobService backgroundJobService;

    BackgroundJobIntegrationTest(
            AppUserRepository appUserRepository,
            BackgroundJobRepository backgroundJobRepository,
            BackgroundJobService backgroundJobService
    ) {
        this.appUserRepository = appUserRepository;
        this.backgroundJobRepository = backgroundJobRepository;
        this.backgroundJobService = backgroundJobService;
    }

    @Test
    void concurrentWorkersCannotClaimSameJob() throws Exception {
        UserId userId = user("concurrent-claim");
        Instant now = Instant.now();
        JobId jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.INGEST_TEXT,
                payload("sourceId"),
                3,
                now.minusSeconds(1)
        );
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<ClaimedBackgroundJobDto>> first = executor.submit(
                    () -> claimWhenReleased("worker-concurrent-a", now, workersReady, start)
            );
            Future<List<ClaimedBackgroundJobDto>> second = executor.submit(
                    () -> claimWhenReleased("worker-concurrent-b", now, workersReady, start)
            );
            workersReady.await();
            start.countDown();

            List<ClaimedBackgroundJobDto> claims = List.of(first.get(), second.get()).stream()
                    .flatMap(List::stream)
                    .toList();

            assertThat(claims)
                    .extracting(ClaimedBackgroundJobDto::jobId)
                    .containsOnlyOnce(jobId);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onlyLeaseOwnerCanCompleteClaimedJob() {
        UserId userId = user("lease-owner");
        Instant now = Instant.now();
        backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.LOOKUP,
                payload("lookupSessionId"),
                3,
                now.minusSeconds(1)
        );
        ClaimedBackgroundJobDto claimed = backgroundJobService.claimBatch(
                "worker-owner", now, Duration.ofMinutes(5), 1
        ).get(0);

        assertThat(backgroundJobService.complete(claimed.jobId(), "worker-other", now.plusSeconds(1))).isFalse();
        assertThat(backgroundJobService.complete(claimed.jobId(), "worker-owner", now.plusSeconds(1))).isTrue();
        assertThat(backgroundJobRepository.findByIdAndUserId(claimed.jobId().value(), userId.value()))
                .get()
                .extracting(BackgroundJobEntity::getState)
                .isEqualTo(BackgroundJobEntity.State.SUCCEEDED);
    }

    @Test
    void expiredLeaseIsRecoveredAndCanBeClaimedAgain() {
        UserId userId = user("lease-recovery");
        Instant now = Instant.now();
        backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.EXPORT_DECK,
                payload("exportId"),
                2,
                now.minusSeconds(1)
        );
        ClaimedBackgroundJobDto firstClaim = backgroundJobService.claimBatch(
                "worker-crashed", now, Duration.ofSeconds(1), 1
        ).get(0);

        assertThat(backgroundJobService.recoverExpiredLeases(now.plusSeconds(2))).isEqualTo(1);
        ClaimedBackgroundJobDto secondClaim = backgroundJobService.claimBatch(
                "worker-recovery", now.plusSeconds(2), Duration.ofMinutes(5), 1
        ).get(0);

        assertThat(secondClaim.jobId()).isEqualTo(firstClaim.jobId());
        assertThat(secondClaim.attempts()).isEqualTo(2);
    }

    @Test
    void statusResultsAreScopedToOwningUser() {
        UserId owner = user("status-owner");
        UserId other = user("status-other");
        JobId ownerJob = backgroundJobService.enqueue(
                owner,
                BackgroundJobEntity.Type.INGEST_TEXT,
                payload("sourceId")
        );
        backgroundJobService.enqueue(other, BackgroundJobEntity.Type.LOOKUP, payload("lookupSessionId"));

        var statuses = backgroundJobService.findRecent(owner, 10);

        assertThat(statuses).extracting(status -> status.id()).containsExactly(ownerJob.value());
    }

    private List<ClaimedBackgroundJobDto> claimWhenReleased(
            String workerId,
            Instant now,
            CountDownLatch workersReady,
            CountDownLatch start
    ) throws InterruptedException {
        workersReady.countDown();
        start.await();
        return backgroundJobService.claimBatch(workerId, now, Duration.ofMinutes(5), 1);
    }

    private UserId user(String label) {
        String uniqueIdentity = label + "-" + UUID.randomUUID();
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(appUserRepository.saveAndFlush(user).getId());
    }

    private Map<String, Object> payload(String idField) {
        return Map.of("schemaVersion", 1, idField, UUID.randomUUID().toString());
    }
}
