package com.example.whatsanki.repository;

import com.example.whatsanki.domain.SourceDocumentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Source queries that preserve user ownership and retention accounting boundaries. */
public interface SourceDocumentRepository extends Repository<SourceDocumentEntity, UUID> {
    SourceDocumentEntity save(SourceDocumentEntity source);
    SourceDocumentEntity saveAndFlush(SourceDocumentEntity source);
    Optional<SourceDocumentEntity> findByIdAndUserId(UUID id, UUID userId);
    /**
     * Estimates row storage for data owned by a source and deleted with it.
     * Operational history, inbound messages, cards, and audit records are
     * intentionally excluded because they survive source deletion.
     */
    @Query(value = """
            select source.source_bytes
                 + coalesce((
                       select sum(pg_column_size(passage))
                       from passage
                       where passage.source_id = source.id
                         and passage.user_id = source.user_id
                   ), 0)
                 + coalesce((
                       select sum(pg_column_size(metadata))
                       from timed_transcript_metadata metadata
                       where metadata.source_id = source.id
                         and metadata.user_id = source.user_id
                   ), 0)
                 + coalesce((
                       select sum(pg_column_size(video))
                       from video_source video
                       where video.source_id = source.id
                         and video.user_id = source.user_id
                   ), 0)
                 + coalesce((
                       select sum(pg_column_size(upload))
                       from source_upload upload
                       where upload.source_id = source.id
                         and upload.user_id = source.user_id
                   ), 0)
                 as stored_bytes
            from (
                select source.id, source.user_id, pg_column_size(source) as source_bytes
                from source_document source
                where source.id = :sourceId and source.user_id = :userId
            ) source
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
                coalesce(sum(
                    source.source_bytes
                    + coalesce((
                          select sum(pg_column_size(passage))
                          from passage
                          where passage.source_id = source.id
                            and passage.user_id = source.user_id
                      ), 0)
                    + coalesce((
                          select sum(pg_column_size(metadata))
                          from timed_transcript_metadata metadata
                          where metadata.source_id = source.id
                            and metadata.user_id = source.user_id
                      ), 0)
                    + coalesce((
                          select sum(pg_column_size(video))
                          from video_source video
                          where video.source_id = source.id
                            and video.user_id = source.user_id
                      ), 0)
                    + coalesce((
                          select sum(pg_column_size(upload))
                          from source_upload upload
                          where upload.source_id = source.id
                            and upload.user_id = source.user_id
                      ), 0)
                ), 0)
            from (
                select source.id, source.user_id, pg_column_size(source) as source_bytes
                from source_document source
                where source.user_id = :userId
                  and source.status <> 'DELETED'
            ) source
            """, nativeQuery = true)
    long totalCorpusStoredBytesByUserId(@Param("userId") UUID userId);
    @Query(value = """
            select source.id as "sourceId",
                   source.source_bytes
                   + coalesce((
                         select sum(pg_column_size(passage))
                         from passage
                         where passage.source_id = source.id
                           and passage.user_id = source.user_id
                     ), 0)
                   + coalesce((
                         select sum(pg_column_size(metadata))
                         from timed_transcript_metadata metadata
                         where metadata.source_id = source.id
                           and metadata.user_id = source.user_id
                     ), 0)
                   + coalesce((
                         select sum(pg_column_size(video))
                         from video_source video
                         where video.source_id = source.id
                           and video.user_id = source.user_id
                     ), 0)
                   + coalesce((
                         select sum(pg_column_size(upload))
                         from source_upload upload
                         where upload.source_id = source.id
                           and upload.user_id = source.user_id
                     ), 0)
                   as "storedBytes"
            from (
                select source.id, source.user_id, pg_column_size(source) as source_bytes
                from source_document source
                where source.user_id = :userId
                  and source.id in (:sourceIds)
            ) source
            """, nativeQuery = true)
    List<SourceStorageProjection> findStoredBytesByUserIdAndSourceIdIn(
            @Param("userId") UUID userId,
            @Param("sourceIds") Collection<UUID> sourceIds
    );
    void delete(SourceDocumentEntity source);
    long deleteByIdAndUserId(UUID id, UUID userId);

    interface SourceStorageProjection {
        UUID getSourceId();
        long getStoredBytes();
    }

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
