alter table source_document drop constraint ck_source_document_kind;
alter table source_document add constraint ck_source_document_kind check (
    kind in ('PASTED_TEXT', 'TXT', 'PDF', 'EPUB', 'YOUTUBE_VIDEO', 'TIMED_TRANSCRIPT')
);

alter table source_document drop constraint ck_source_document_status;
alter table source_document add constraint ck_source_document_status check (
    status in ('RECEIVED', 'FETCHING', 'STORED', 'PARSING', 'NORMALIZING', 'EXTRACTING', 'INDEXING',
               'READY', 'FAILED', 'DELETING', 'DELETED')
);

alter table background_job drop constraint ck_background_job_type;
alter table background_job add constraint ck_background_job_type check (
    type in ('FETCH_MEDIA', 'EXTRACT_DOCUMENT', 'INGEST_TEXT', 'INGEST_TIMED_TRANSCRIPT', 'FETCH_VIDEO_SUBTITLES',
             'LOOKUP', 'DELIVER_LOOKUP_RESULT', 'EXPORT_DECK', 'DELETE_TWILIO_MEDIA',
             'PURGE_SOURCE_OBJECT', 'DELETE_SOURCE', 'DELETE_USER_DATA')
);

create table timed_transcript_metadata (
    source_id uuid primary key,
    user_id uuid not null,
    original_filename varchar(512) not null,
    format varchar(8) not null,
    language varchar(35) not null,
    origin_url varchar(2048),
    origin_provider varchar(24),
    origin_provider_id varchar(32),
    timeline_offset_ms bigint not null default 0,
    cue_count integer not null default 0,
    normalized_character_count bigint not null default 0,
    timeline_end_ms bigint not null default 0,
    parser_version varchar(32) not null,
    normalizer_version varchar(32) not null,
    ignored_structural_block_count integer not null default 0,
    stripped_markup_count integer not null default 0,
    empty_cue_count integer not null default 0,
    coalesced_cue_count integer not null default 0,
    temporary_object_key varchar(1024),
    constraint fk_timed_transcript_metadata_owner foreign key (source_id, user_id)
        references source_document(id, user_id) on delete cascade,
    constraint ck_timed_transcript_metadata_format check (format in ('VTT', 'SRT')),
    constraint ck_timed_transcript_metadata_offset check (timeline_offset_ms >= 0),
    constraint ck_timed_transcript_metadata_counts check (
        cue_count >= 0 and normalized_character_count >= 0 and timeline_end_ms >= 0
    ),
    constraint ck_timed_transcript_metadata_origin check (
        (origin_provider is null and origin_provider_id is null)
        or (origin_provider = 'YOUTUBE' and origin_provider_id ~ '^[A-Za-z0-9_-]{11}$')
    ),
    constraint ck_timed_transcript_metadata_warning_counts check (
        ignored_structural_block_count >= 0 and stripped_markup_count >= 0 and empty_cue_count >= 0 and coalesced_cue_count >= 0
    )
);

create index idx_background_job_timed_transcript_reference on background_job(user_id, (payload ->> 'sourceId'), state)
    where type = 'INGEST_TIMED_TRANSCRIPT' and payload ? 'sourceId';
