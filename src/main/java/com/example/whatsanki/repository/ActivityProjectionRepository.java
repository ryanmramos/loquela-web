package com.example.whatsanki.repository;

import com.example.whatsanki.domain.SourceDocumentEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only union projection; activity remains owned by its source domain tables. */
public interface ActivityProjectionRepository extends Repository<SourceDocumentEntity, UUID> {

    @Query(value = """
            with logical_activity as (
                select source.id,
                       'SOURCE'::text as category,
                       case when source.status in ('DELETING', 'DELETED')
                            then 'DELETE_SOURCE' else 'INGEST_SOURCE' end::text as operation,
                       case when source.status = 'FAILED' then 'FAILED'
                            when source.status in ('READY', 'DELETED') then 'COMPLETED'
                            else 'ACTIVE' end::text as lifecycle,
                       source.status::text as status,
                       source.display_name::text as resource_label,
                       null::text as delivery_status,
                       source.created_at,
                       source.updated_at
                from source_document source
                where source.user_id = :userId

                union all

                select lookup.id,
                       'LOOKUP'::text as category,
                       'LOOKUP'::text as operation,
                       case
                           when lookup.status = 'FAILED' then 'FAILED'
                           when case when lookup.inbound_message_id is null then 'NOT_APPLICABLE'
                                     when bool_or(outbound.status = 'FAILED') then 'FAILED'
                                     when count(outbound.id) > 0 and bool_and(outbound.status = 'PROVIDER_ACCEPTED')
                                          then 'PROVIDER_ACCEPTED'
                                     else 'PENDING' end = 'FAILED' then 'FAILED'
                           when lookup.status = 'EXPIRED' then 'COMPLETED'
                           when lookup.status = 'READY' and
                                case when lookup.inbound_message_id is null then 'NOT_APPLICABLE'
                                     when bool_or(outbound.status = 'FAILED') then 'FAILED'
                                     when count(outbound.id) > 0 and bool_and(outbound.status = 'PROVIDER_ACCEPTED')
                                          then 'PROVIDER_ACCEPTED'
                                     else 'PENDING' end in ('NOT_APPLICABLE', 'PROVIDER_ACCEPTED') then 'COMPLETED'
                           else 'ACTIVE'
                       end::text as lifecycle,
                       lookup.status::text as status,
                       lookup.requested_term::text as resource_label,
                       case when lookup.inbound_message_id is null then 'NOT_APPLICABLE'
                            when bool_or(outbound.status = 'FAILED') then 'FAILED'
                            when count(outbound.id) > 0 and bool_and(outbound.status = 'PROVIDER_ACCEPTED')
                                 then 'PROVIDER_ACCEPTED'
                            else 'PENDING' end::text as delivery_status,
                       lookup.created_at,
                       greatest(lookup.updated_at, coalesce(max(outbound.updated_at), lookup.updated_at)) as updated_at
                from lookup_session lookup
                left join outbound_message outbound
                  on outbound.lookup_session_id = lookup.id
                 and outbound.user_id = lookup.user_id
                where lookup.user_id = :userId
                group by lookup.id

                union all

                select export.id,
                       'EXPORT'::text as category,
                       'EXPORT_DECK'::text as operation,
                       case when export.state = 'FAILED' then 'FAILED'
                            when export.state in ('READY', 'EXPIRED') then 'COMPLETED'
                            else 'ACTIVE' end::text as lifecycle,
                       export.state::text as status,
                       deck.name::text as resource_label,
                       null::text as delivery_status,
                       export.created_at,
                       export.updated_at
                from export_request export
                join deck on deck.id = export.deck_id and deck.user_id = export.user_id
                where export.user_id = :userId
            )
            select id,
                   category,
                   operation,
                   lifecycle,
                   status,
                   resource_label as "resourceLabel",
                   delivery_status as "deliveryStatus",
                   created_at as "createdAt",
                   updated_at as "updatedAt"
            from logical_activity
            where (cast(:category as text) is null or category = cast(:category as text))
              and (cast(:lifecycle as text) is null or lifecycle = cast(:lifecycle as text))
              and (
                    cast(:cursorUpdatedAt as timestamptz) is null
                    or updated_at < cast(:cursorUpdatedAt as timestamptz)
                    or (updated_at = cast(:cursorUpdatedAt as timestamptz) and category > cast(:cursorCategory as text))
                    or (updated_at = cast(:cursorUpdatedAt as timestamptz)
                        and category = cast(:cursorCategory as text)
                        and id > cast(:cursorId as uuid))
              )
            order by updated_at desc, category, id
            limit :limit
            """, nativeQuery = true)
    List<ActivityRow> findPage(
            @Param("userId") UUID userId,
            @Param("category") String category,
            @Param("lifecycle") String lifecycle,
            @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
            @Param("cursorCategory") String cursorCategory,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit
    );

    @Query(value = """
            select (
                select count(*) from source_document
                where user_id = :userId and status not in ('READY', 'FAILED', 'DELETED')
            ) + (
                select count(*)
                from lookup_session lookup
                where lookup.user_id = :userId
                  and lookup.status = 'PENDING'
                  and not exists (
                      select 1 from outbound_message outbound
                      where outbound.user_id = lookup.user_id
                        and outbound.lookup_session_id = lookup.id
                        and outbound.status = 'FAILED'
                  )
            ) + (
                select count(*)
                from lookup_session lookup
                where lookup.user_id = :userId
                  and lookup.status = 'READY'
                  and lookup.inbound_message_id is not null
                  and not exists (
                      select 1 from outbound_message outbound
                      where outbound.user_id = lookup.user_id
                        and outbound.lookup_session_id = lookup.id
                        and outbound.status = 'FAILED'
                  )
                  and (
                      not exists (
                          select 1 from outbound_message outbound
                          where outbound.user_id = lookup.user_id
                            and outbound.lookup_session_id = lookup.id
                      )
                      or exists (
                          select 1 from outbound_message outbound
                          where outbound.user_id = lookup.user_id
                            and outbound.lookup_session_id = lookup.id
                            and outbound.status <> 'PROVIDER_ACCEPTED'
                      )
                  )
            ) + (
                select count(*) from export_request
                where user_id = :userId and state in ('QUEUED', 'RUNNING')
            )
            """, nativeQuery = true)
    long countActive(@Param("userId") UUID userId);

    interface ActivityRow {
        UUID getId();
        String getCategory();
        String getOperation();
        String getLifecycle();
        String getStatus();
        String getResourceLabel();
        String getDeliveryStatus();
        Instant getCreatedAt();
        Instant getUpdatedAt();
    }
}
