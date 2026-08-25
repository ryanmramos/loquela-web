package com.example.whatsanki.repository;

import com.example.whatsanki.domain.LookupSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/** Provides locked lookup reads used by idempotent card saves and worker lifecycle steps. */
public interface LookupSessionRepository extends Repository<LookupSessionEntity, UUID> {
    LookupSessionEntity save(LookupSessionEntity session);
    Optional<LookupSessionEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<LookupSessionEntity> findFirstByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId,
            LookupSessionEntity.Status status,
            Instant now
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from LookupSessionEntity session
            where session.userId = :userId
              and session.status = :status
              and session.expiresAt > :now
            order by session.createdAt desc
            limit 1
            """)
    Optional<LookupSessionEntity> findLatestForUpdate(
            @Param("userId") UUID userId,
            @Param("status") LookupSessionEntity.Status status,
            @Param("now") Instant now
    );
    Optional<LookupSessionEntity> findByIdAndUserId(UUID id, UUID userId);
    List<LookupSessionEntity> findAllByUserIdAndIdIn(UUID userId, Set<UUID> ids);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from LookupSessionEntity session where session.id = :id and session.userId = :userId")
    Optional<LookupSessionEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
    Optional<LookupSessionEntity> findByUserIdAndInboundMessageId(UUID userId, UUID inboundMessageId);
}
