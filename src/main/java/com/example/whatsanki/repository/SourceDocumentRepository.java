package com.example.whatsanki.repository;

import com.example.whatsanki.domain.SourceDocumentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SourceDocumentRepository extends Repository<SourceDocumentEntity, UUID> {
    SourceDocumentEntity save(SourceDocumentEntity source);
    SourceDocumentEntity saveAndFlush(SourceDocumentEntity source);
    Optional<SourceDocumentEntity> findByIdAndUserId(UUID id, UUID userId);
    @Query(value = """
            select pg_column_size(source_document)
            from source_document
            where id = :sourceId and user_id = :userId
            """, nativeQuery = true)
    long storedBytesByIdAndUserId(@Param("sourceId") UUID sourceId, @Param("userId") UUID userId);
    Optional<SourceDocumentEntity> findByUserIdAndInboundMessageId(UUID userId, UUID inboundMessageId);
    List<SourceDocumentEntity> findAllByUserIdAndIdIn(UUID userId, Set<UUID> ids);
    List<SourceDocumentEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<SourceDocumentEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    List<SourceDocumentEntity> findAllByUserIdAndStatusAndLanguageOrderByDisplayName(
            UUID userId, SourceDocumentEntity.Status status, String language);
    @Query("""
            select source
            from SourceDocumentEntity source
            where source.userId = :userId
              and source.status <> com.example.whatsanki.domain.SourceDocumentEntity.Status.DELETED
              and lower(source.displayName) like lower(concat('%', :search, '%'))
              and (:kind is null or source.kind = :kind)
              and (:status is null or source.status = :status)
            """)
    Page<SourceDocumentEntity> findBrowserPage(
            @Param("userId") UUID userId,
            @Param("search") String search,
            @Param("kind") SourceDocumentEntity.Kind kind,
            @Param("status") SourceDocumentEntity.Status status,
            Pageable pageable
    );
    long countByUserIdAndStatusNot(UUID userId, SourceDocumentEntity.Status status);
    long countByUserIdAndStatus(UUID userId, SourceDocumentEntity.Status status);
    long countByUserIdAndStatusIn(UUID userId, Set<SourceDocumentEntity.Status> statuses);
    @Query(value = """
            select
                coalesce(sum(pg_column_size(source)), 0)
                + coalesce((
                    select sum(pg_column_size(passage))
                    from passage
                    join source_document passage_source
                      on passage_source.id = passage.source_id
                     and passage_source.user_id = passage.user_id
                    where passage.user_id = :userId
                      and passage_source.status <> 'DELETED'
                ), 0)
            from source_document source
            where source.user_id = :userId
              and source.status <> 'DELETED'
            """, nativeQuery = true)
    long totalCorpusStoredBytesByUserId(@Param("userId") UUID userId);
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
