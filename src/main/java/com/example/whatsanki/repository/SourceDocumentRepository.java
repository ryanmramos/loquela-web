package com.example.whatsanki.repository;

import com.example.whatsanki.domain.SourceDocumentEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceDocumentRepository extends Repository<SourceDocumentEntity, UUID> {
    SourceDocumentEntity save(SourceDocumentEntity source);
    Optional<SourceDocumentEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<SourceDocumentEntity> findByUserIdAndInboundMessageId(UUID userId, UUID inboundMessageId);
    List<SourceDocumentEntity> findAllByUserIdAndIdIn(UUID userId, Set<UUID> ids);
    List<SourceDocumentEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<SourceDocumentEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    void delete(SourceDocumentEntity source);
    long deleteByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            select *
            from source_document
            where object_key is not null
              and status in ('READY', 'FAILED')
              and original_stored_at <= :cutoff
              and not exists (
                  select 1
                  from background_job job
                  where job.user_id = source_document.user_id
                    and job.type = 'PURGE_SOURCE_OBJECT'
                    and job.state in ('QUEUED', 'RUNNING', 'RETRY_WAIT')
                    and job.payload ->> 'sourceId' = source_document.id::text
              )
            order by original_stored_at
            """, nativeQuery = true)
    List<SourceDocumentEntity> findOriginalRetentionCandidates(
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );
}
