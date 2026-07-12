create table background_job (
    id uuid primary key,
    user_id uuid,
    type varchar(64) not null,
    state varchar(24) not null,
    payload jsonb not null default '{}'::jsonb,
    attempts integer not null default 0,
    max_attempts integer not null,
    run_after timestamptz not null,
    lease_owner varchar(128),
    lease_expires_at timestamptz,
    last_error_code varchar(64),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_background_job_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint ck_background_job_type check (
        type in ('FETCH_MEDIA', 'INGEST_TEXT', 'LOOKUP', 'EXPORT_DECK', 'DELETE_SOURCE', 'DELETE_USER_DATA')
    ),
    constraint ck_background_job_state check (
        state in ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'DEAD', 'CANCELLED')
    ),
    constraint ck_background_job_attempts check (attempts >= 0 and max_attempts > 0 and attempts <= max_attempts)
);

create index idx_background_job_claim
    on background_job(state, run_after, created_at);
create index idx_background_job_user_created
    on background_job(user_id, created_at desc);

