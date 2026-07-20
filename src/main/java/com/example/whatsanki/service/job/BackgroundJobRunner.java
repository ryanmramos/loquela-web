package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "whatsanki.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class BackgroundJobRunner {

    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobDispatcher backgroundJobDispatcher;
    private final WhatsAnkiConfigurationDto configuration;
    private final String workerId;

    public BackgroundJobRunner(
            BackgroundJobService backgroundJobService,
            BackgroundJobDispatcher backgroundJobDispatcher,
            WhatsAnkiConfigurationDto configuration
    ) {
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobDispatcher = backgroundJobDispatcher;
        this.configuration = configuration;
        workerId = "worker-" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${whatsanki.jobs.poll-interval:PT1S}")
    public void poll() {
        Instant now = Instant.now();
        backgroundJobService.recoverExpiredLeases(now);
        List<ClaimedBackgroundJobDto> jobs = backgroundJobService.claimBatch(
                workerId,
                now,
                configuration.getJobs().getLeaseDuration(),
                configuration.getJobs().getBatchSize()
        );
        jobs.forEach(this::execute);
    }

    private void execute(ClaimedBackgroundJobDto job) {
        try {
            // Claiming has already committed. Slow I/O or parsing therefore happens
            // without holding row locks or an open database transaction.
            backgroundJobDispatcher.dispatch(job);
            backgroundJobService.complete(job.jobId(), workerId, Instant.now());
        } catch (BackgroundJobExecutionException exception) {
            backgroundJobService.fail(
                    job,
                    workerId,
                    exception.failureKind(),
                    exception.errorCode(),
                    Instant.now()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Background job handler raised an unexpected exception jobId={} jobType={} exceptionType={}",
                    job.jobId().value(),
                    job.type(),
                    exception.getClass().getSimpleName()
            );
            backgroundJobService.fail(
                    job,
                    workerId,
                    BackgroundJobFailureKind.TRANSIENT,
                    "UNEXPECTED_JOB_ERROR",
                    Instant.now()
            );
        }
    }
}
