create table export_request (
    id uuid primary key,
    user_id uuid not null,
    deck_id uuid not null,
    state varchar(24) not null,
    snapshot_payload jsonb not null,
    snapshot_sha256 bytea not null,
    artifact_key varchar(1024),
    artifact_sha256 bytea,
    template_version varchar(64) not null,
    expires_at timestamptz,
    error_code varchar(64),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_export_request_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint fk_export_request_deck_owner foreign key (deck_id, user_id)
        references deck(id, user_id) on delete cascade,
    constraint ck_export_request_state check (state in ('QUEUED', 'RUNNING', 'READY', 'FAILED', 'EXPIRED')),
    constraint ck_export_request_ready_artifact check (
        state <> 'READY' or (artifact_key is not null and artifact_sha256 is not null and expires_at is not null)
    )
);

create index idx_export_request_user_created on export_request(user_id, created_at desc);
create index idx_export_request_expiry on export_request(expires_at) where expires_at is not null;

