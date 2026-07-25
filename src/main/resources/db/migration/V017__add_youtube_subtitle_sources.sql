alter table inbound_message drop constraint ck_inbound_message_command_type;
alter table inbound_message add constraint ck_inbound_message_command_type check (
    command_type is null or command_type in (
        'HELP', 'SETTINGS', 'LANGUAGE', 'ADD', 'LOOKUP', 'SAVE',
        'SOURCES', 'DELETE_SOURCE', 'EXPORT', 'STATUS', 'DELETE_MY_DATA',
        'YOUTUBE', 'DOCUMENT', 'UNKNOWN'
    )
);

alter table source_document drop constraint ck_source_document_kind;
alter table source_document add constraint ck_source_document_kind check (
    kind in ('PASTED_TEXT', 'TXT', 'PDF', 'EPUB', 'YOUTUBE_VIDEO')
);

alter table background_job drop constraint ck_background_job_type;
alter table background_job add constraint ck_background_job_type check (
    type in (
        'FETCH_MEDIA', 'EXTRACT_DOCUMENT', 'INGEST_TEXT', 'FETCH_VIDEO_SUBTITLES',
        'LOOKUP', 'DELIVER_LOOKUP_RESULT', 'EXPORT_DECK', 'DELETE_TWILIO_MEDIA',
        'PURGE_SOURCE_OBJECT', 'DELETE_SOURCE', 'DELETE_USER_DATA'
    )
);

create table video_source (
    source_id uuid primary key,
    user_id uuid not null,
    provider varchar(24) not null,
    provider_video_id varchar(32) not null,
    canonical_url varchar(512) not null,
    title varchar(512),
    channel_name varchar(512),
    duration_ms bigint,
    caption_language varchar(35) not null,
    caption_kind varchar(24) not null,
    provider_version varchar(64),
    retrieved_at timestamptz,
    constraint fk_video_source_owner foreign key (source_id, user_id)
        references source_document(id, user_id) on delete cascade,
    constraint uk_video_source_user_provider_language
        unique (user_id, provider, provider_video_id, caption_language),
    constraint ck_video_source_provider check (provider = 'YOUTUBE'),
    constraint ck_video_source_video_id check (provider_video_id ~ '^[A-Za-z0-9_-]{11}$'),
    constraint ck_video_source_duration check (duration_ms is null or duration_ms >= 0),
    constraint ck_video_source_caption_kind check (caption_kind in ('PENDING', 'MANUAL', 'AUTOMATIC'))
);

create index idx_background_job_video_source_reference
    on background_job(user_id, (payload ->> 'sourceId'), state)
    where type = 'FETCH_VIDEO_SUBTITLES' and payload ? 'sourceId';
