alter table lookup_session
    add column inbound_message_id uuid,
    add column status varchar(24),
    add column error_code varchar(64),
    add column updated_at timestamptz;

-- Any lookup rows from an earlier application build already contain a result snapshot.
update lookup_session
set status = 'READY',
    updated_at = created_at
where status is null;

alter table lookup_session
    alter column status set not null,
    alter column updated_at set not null,
    add constraint ck_lookup_session_status check (status in ('PENDING', 'READY', 'FAILED', 'EXPIRED')),
    add constraint ck_lookup_session_error_state check (
        (status = 'FAILED' and error_code is not null)
        or (status <> 'FAILED' and error_code is null)
    ),
    add constraint fk_lookup_session_inbound_owner foreign key (inbound_message_id, user_id)
        references inbound_message(id, user_id) on delete set null (inbound_message_id);

create unique index uk_lookup_session_user_inbound
    on lookup_session(user_id, inbound_message_id)
    where inbound_message_id is not null;

create index idx_lookup_session_user_ready_expiry
    on lookup_session(user_id, expires_at desc)
    where status = 'READY';

create index idx_background_job_lookup_reference
    on background_job(user_id, (payload ->> 'lookupSessionId'), state)
    where type = 'LOOKUP' and payload ? 'lookupSessionId';
