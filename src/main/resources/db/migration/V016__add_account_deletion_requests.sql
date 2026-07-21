create table account_deletion_request (
    id uuid primary key,
    user_id uuid not null,
    confirmation_code_hash bytea not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_account_deletion_request_user unique (user_id),
    constraint fk_account_deletion_request_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint ck_account_deletion_request_expiry check (expires_at > created_at)
);

create index idx_account_deletion_request_expiry
    on account_deletion_request(expires_at);
