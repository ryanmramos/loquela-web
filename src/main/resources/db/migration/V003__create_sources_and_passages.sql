create table source_document (
    id uuid primary key,
    user_id uuid not null,
    kind varchar(32) not null,
    display_name varchar(512) not null,
    status varchar(24) not null,
    detected_media_type varchar(128),
    object_key varchar(1024),
    sha256 bytea,
    language varchar(35),
    character_count bigint,
    passage_count integer,
    metadata jsonb not null default '{}'::jsonb,
    error_code varchar(64),
    error_detail_safe varchar(512),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_source_document_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint uk_source_document_id_user unique (id, user_id),
    constraint ck_source_document_kind check (kind in ('PASTED_TEXT', 'TXT', 'PDF', 'EPUB')),
    constraint ck_source_document_status check (
        status in ('RECEIVED', 'FETCHING', 'EXTRACTING', 'INDEXING', 'READY', 'FAILED', 'DELETING', 'DELETED')
    ),
    constraint ck_source_document_counts check (
        (character_count is null or character_count >= 0)
        and (passage_count is null or passage_count >= 0)
    )
);

create index idx_source_document_user_status_created
    on source_document(user_id, status, created_at desc);

create table passage (
    id uuid primary key,
    user_id uuid not null,
    source_id uuid not null,
    ordinal integer not null,
    display_text text not null,
    normalized_text text not null,
    search_vector tsvector generated always as (to_tsvector('simple', normalized_text)) stored,
    location jsonb not null default '{}'::jsonb,
    token_count integer not null,
    created_at timestamptz not null,
    constraint fk_passage_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint fk_passage_source_owner foreign key (source_id, user_id)
        references source_document(id, user_id) on delete cascade,
    constraint uk_passage_source_ordinal unique (source_id, ordinal),
    constraint ck_passage_ordinal check (ordinal >= 0),
    constraint ck_passage_token_count check (token_count >= 0)
);

create index idx_passage_user_source on passage(user_id, source_id);
create index idx_passage_search_vector on passage using gin(search_vector);

