create table access_entry (
    id uuid primary key,
    normalized_email varchar(320) not null,
    role varchar(24) not null,
    status varchar(24) not null,
    version bigint not null default 0,
    created_by_user_id uuid,
    updated_by_user_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_access_entry_email unique (normalized_email),
    constraint fk_access_entry_created_by foreign key (created_by_user_id)
        references app_user(id) on delete set null,
    constraint fk_access_entry_updated_by foreign key (updated_by_user_id)
        references app_user(id) on delete set null,
    constraint ck_access_entry_role check (role in ('USER', 'ADMIN')),
    constraint ck_access_entry_status check (status in ('ACTIVE', 'REVOKED')),
    constraint ck_access_entry_email_normalized check (
        normalized_email = lower(btrim(normalized_email))
        and normalized_email like '%@%'
    )
);

create index idx_access_entry_status_role_updated
    on access_entry(status, role, updated_at desc);

create table access_entry_audit_event (
    id uuid primary key,
    access_entry_id uuid not null,
    actor_user_id uuid,
    action varchar(32) not null,
    previous_role varchar(24),
    new_role varchar(24),
    previous_status varchar(24),
    new_status varchar(24),
    correlation_id varchar(128),
    created_at timestamptz not null,
    constraint fk_access_audit_entry foreign key (access_entry_id)
        references access_entry(id) on delete restrict,
    constraint fk_access_audit_actor foreign key (actor_user_id)
        references app_user(id) on delete set null,
    constraint ck_access_audit_action check (
        action in ('BOOTSTRAPPED', 'CREATED', 'ROLE_CHANGED', 'REVOKED', 'REACTIVATED')
    )
);

create index idx_access_audit_entry_created
    on access_entry_audit_event(access_entry_id, created_at desc);

