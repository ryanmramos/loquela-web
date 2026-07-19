package com.example.whatsanki.repository;

import com.example.whatsanki.domain.BackgroundJobEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackgroundJobRepository extends Repository<BackgroundJobEntity, UUID> {
    BackgroundJobEntity save(BackgroundJobEntity job);

    Optional<BackgroundJobEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            select *
            from background_job
            where user_id = :userId
            order by case state
                when 'RUNNING' then 0
                when 'QUEUED' then 1
                when 'RETRY_WAIT' then 2
                else 3
            end, created_at desc
            limit :limit
            """, nativeQuery = true)
    List<BackgroundJobEntity> findRecentByUserId(
            @Param("userId") UUID userId,
            @Param("limit") int limit
    );

    @Query(value = """
            with candidates as (
                select id
                from background_job
                where state in ('QUEUED', 'RETRY_WAIT')
                  and run_after <= :now
                  and attempts < max_attempts
                order by run_after, created_at
                for update skip locked
                limit :batchSize
            )
            update background_job job
            set state = 'RUNNING',
                attempts = job.attempts + 1,
                lease_owner = :workerId,
                lease_expires_at = :leaseExpiresAt,
                last_error_code = null,
                updated_at = :now
            from candidates
            where job.id = candidates.id
            returning job.*
            """, nativeQuery = true)
    List<BackgroundJobEntity> claimBatch(
            @Param("workerId") String workerId,
            @Param("now") Instant now,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("batchSize") int batchSize
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update background_job
            set state = 'SUCCEEDED',
                lease_owner = null,
                lease_expires_at = null,
                last_error_code = null,
                updated_at = :now
            where id = :jobId
              and state = 'RUNNING'
              and lease_owner = :workerId
            """, nativeQuery = true)
    int markSucceeded(
            @Param("jobId") UUID jobId,
            @Param("workerId") String workerId,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update background_job
            set state = 'RETRY_WAIT',
                run_after = :runAfter,
                lease_owner = null,
                lease_expires_at = null,
                last_error_code = :errorCode,
                updated_at = :now
            where id = :jobId
              and state = 'RUNNING'
              and lease_owner = :workerId
            """, nativeQuery = true)
    int markRetryWaiting(
            @Param("jobId") UUID jobId,
            @Param("workerId") String workerId,
            @Param("errorCode") String errorCode,
            @Param("runAfter") Instant runAfter,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update background_job
            set state = 'DEAD',
                lease_owner = null,
                lease_expires_at = null,
                last_error_code = :errorCode,
                updated_at = :now
            where id = :jobId
              and state = 'RUNNING'
              and lease_owner = :workerId
            """, nativeQuery = true)
    int markDead(
            @Param("jobId") UUID jobId,
            @Param("workerId") String workerId,
            @Param("errorCode") String errorCode,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update background_job
            set state = case
                    when attempts >= max_attempts then 'DEAD'
                    else 'QUEUED'
                end,
                run_after = :now,
                lease_owner = null,
                lease_expires_at = null,
                last_error_code = case
                    when attempts >= max_attempts then 'LEASE_EXPIRED'
                    else null
                end,
                updated_at = :now
            where state = 'RUNNING'
              and lease_expires_at <= :now
            """, nativeQuery = true)
    int recoverExpiredLeases(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update background_job
            set state = 'CANCELLED',
                lease_owner = null,
                lease_expires_at = null,
                updated_at = :now
            where user_id = :userId
              and state in ('QUEUED', 'RETRY_WAIT')
              and payload ->> 'sourceId' = :sourceId
              and (:excludedJobId is null or id <> :excludedJobId)
            """, nativeQuery = true)
    int cancelPendingForSource(
            @Param("userId") UUID userId,
            @Param("sourceId") String sourceId,
            @Param("excludedJobId") UUID excludedJobId,
            @Param("now") Instant now
    );

    @Query(value = """
            select count(*)
            from background_job
            where user_id = :userId
              and state = 'RUNNING'
              and payload ->> 'sourceId' = :sourceId
              and id <> :excludedJobId
            """, nativeQuery = true)
    long countRunningForSource(
            @Param("userId") UUID userId,
            @Param("sourceId") String sourceId,
            @Param("excludedJobId") UUID excludedJobId
    );

    @Query(value = """
            select exists (
                select 1
                from background_job
                where user_id = :userId
                  and type = :jobType
                  and state in ('QUEUED', 'RUNNING', 'RETRY_WAIT')
                  and payload ->> 'sourceId' = :sourceId
            )
            """, nativeQuery = true)
    boolean existsActiveForSourceAndType(
            @Param("userId") UUID userId,
            @Param("sourceId") String sourceId,
            @Param("jobType") String jobType
    );

    @Query(value = """
            select exists (
                select 1
                from background_job
                where user_id = :userId
                  and type = :jobType
                  and state in ('QUEUED', 'RUNNING', 'RETRY_WAIT')
                  and payload ->> 'inboundMessageId' = :inboundMessageId
            )
            """, nativeQuery = true)
    boolean existsActiveForInboundMessageAndType(
            @Param("userId") UUID userId,
            @Param("inboundMessageId") String inboundMessageId,
            @Param("jobType") String jobType
    );
}
