alter table background_job
    add constraint ck_background_job_lease_fields check (
        (
            state = 'RUNNING'
            and lease_owner is not null
            and lease_expires_at is not null
        ) or (
            state <> 'RUNNING'
            and lease_owner is null
            and lease_expires_at is null
        )
    ),
    add constraint ck_background_job_error_code check (
        last_error_code is null or last_error_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
    );

create index idx_background_job_ready
    on background_job(run_after, created_at)
    where state in ('QUEUED', 'RETRY_WAIT');

create index idx_background_job_expired_lease
    on background_job(lease_expires_at)
    where state = 'RUNNING';
