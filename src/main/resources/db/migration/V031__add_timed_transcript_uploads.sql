-- Reuse the existing, owner-scoped one-time upload intent. Keeping one intent
-- state machine avoids a second table with subtly different completion and
-- expiry guarantees.
alter table source_upload
    add column purpose varchar(24) not null default 'DOCUMENT',
    add column transcript_format varchar(8),
    add column language varchar(35),
    add column origin_url varchar(2048),
    add column timeline_offset_ms bigint;

alter table source_upload add constraint ck_source_upload_purpose
    check (purpose in ('DOCUMENT', 'TIMED_TRANSCRIPT'));

alter table source_upload add constraint ck_source_upload_transcript_fields check (
    (purpose = 'DOCUMENT'
        and transcript_format is null
        and language is null
        and origin_url is null
        and timeline_offset_ms is null)
    or
    (purpose = 'TIMED_TRANSCRIPT'
        and transcript_format in ('VTT', 'SRT')
        and language is not null
        and length(btrim(language)) > 0
        and timeline_offset_ms >= 0)
);
