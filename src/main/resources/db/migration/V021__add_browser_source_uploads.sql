create table source_upload (
    id uuid primary key,
    user_id uuid not null,
    display_name varchar(512) not null,
    declared_media_type varchar(128) not null,
    declared_size bigint not null,
    declared_sha256 bytea not null,
    state varchar(24) not null,
    source_id uuid,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    version bigint not null default 0,
    constraint fk_source_upload_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint fk_source_upload_source_owner foreign key (source_id, user_id)
        references source_document(id, user_id) on delete cascade,
    constraint uk_source_upload_source unique (source_id),
    constraint ck_source_upload_size check (declared_size > 0),
    constraint ck_source_upload_state check (state in ('PENDING', 'COMPLETED', 'EXPIRED')),
    constraint ck_source_upload_completion check (
        (state = 'PENDING' and source_id is null and completed_at is null)
        or (state = 'COMPLETED' and source_id is not null and completed_at is not null)
        or (state = 'EXPIRED' and source_id is null and completed_at is null)
    ),
    constraint ck_source_upload_version check (version >= 0)
);

create index idx_source_upload_user_created
    on source_upload(user_id, created_at desc);

create index idx_source_upload_expiry
    on source_upload(state, expires_at)
    where state = 'PENDING';

create index idx_source_document_user_kind_status_created
    on source_document(user_id, kind, status, created_at desc);

create index idx_source_document_user_display_name
    on source_document(user_id, lower(display_name));
