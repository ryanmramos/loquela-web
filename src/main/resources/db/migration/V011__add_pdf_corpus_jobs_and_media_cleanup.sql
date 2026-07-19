alter table inbound_message
    add column twilio_media_sid varchar(34),
    add constraint ck_inbound_message_twilio_media_sid check (
        twilio_media_sid is null or twilio_media_sid ~ '^ME[0-9a-fA-F]{32}$'
    );

alter table source_document
    add column original_stored_at timestamptz;

-- Existing STORED rows predate the extraction job. Preserve their effective storage
-- time and queue an extraction so an upgrade does not strand an uploaded PDF forever.
update source_document
set original_stored_at = updated_at
where object_key is not null;

alter table source_document
    add constraint ck_source_document_object_storage_time check (
        object_key is null or original_stored_at is not null
    );

alter table background_job
    drop constraint ck_background_job_type;

alter table background_job
    add constraint ck_background_job_type check (
        type in (
            'FETCH_MEDIA', 'EXTRACT_DOCUMENT', 'INGEST_TEXT', 'LOOKUP',
            'EXPORT_DECK', 'DELETE_TWILIO_MEDIA', 'PURGE_SOURCE_OBJECT',
            'DELETE_SOURCE', 'DELETE_USER_DATA'
        )
    );

insert into background_job (
    id, user_id, type, state, payload, attempts, max_attempts, run_after,
    created_at, updated_at
)
select
    gen_random_uuid(), source.user_id, 'EXTRACT_DOCUMENT', 'QUEUED',
    jsonb_build_object('schemaVersion', 1, 'sourceId', source.id::text),
    0, 3, now(), now(), now()
from source_document source
where source.kind = 'PDF'
  and source.status = 'STORED'
  and source.object_key is not null
  and not exists (
      select 1
      from background_job job
      where job.user_id = source.user_id
        and job.type = 'EXTRACT_DOCUMENT'
        and job.state in ('QUEUED', 'RUNNING', 'RETRY_WAIT')
        and job.payload ->> 'sourceId' = source.id::text
  );

create index idx_inbound_message_pending_media_cleanup
    on inbound_message(received_at)
    where twilio_media_sid is not null and media_url_ciphertext is null;

create index idx_background_job_inbound_reference
    on background_job(user_id, (payload ->> 'inboundMessageId'), state)
    where payload ? 'inboundMessageId';

create index idx_source_document_original_retention
    on source_document(original_stored_at)
    where object_key is not null
      and original_stored_at is not null
      and status in ('READY', 'FAILED');
