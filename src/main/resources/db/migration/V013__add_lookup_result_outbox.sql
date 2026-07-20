alter table lookup_session
    add constraint uk_lookup_session_id_user unique (id, user_id);

alter table background_job
    drop constraint ck_background_job_type;

alter table background_job
    add constraint ck_background_job_type check (
        type in (
            'FETCH_MEDIA', 'EXTRACT_DOCUMENT', 'INGEST_TEXT', 'LOOKUP',
            'DELIVER_LOOKUP_RESULT', 'EXPORT_DECK', 'DELETE_TWILIO_MEDIA',
            'PURGE_SOURCE_OBJECT', 'DELETE_SOURCE', 'DELETE_USER_DATA'
        )
    );

create table outbound_message (
    id uuid primary key,
    user_id uuid not null,
    lookup_session_id uuid not null,
    part_number integer not null,
    body text not null,
    status varchar(24) not null,
    provider_message_sid varchar(128),
    error_code varchar(64),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    provider_accepted_at timestamptz,
    constraint fk_outbound_message_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint fk_outbound_message_lookup_owner foreign key (lookup_session_id, user_id)
        references lookup_session(id, user_id) on delete cascade,
    constraint uk_outbound_message_lookup_part unique (lookup_session_id, part_number),
    constraint ck_outbound_message_part check (part_number > 0),
    constraint ck_outbound_message_body check (char_length(body) > 0),
    constraint ck_outbound_message_status check (
        status in ('PENDING', 'PROVIDER_ACCEPTED', 'FAILED')
    ),
    constraint ck_outbound_message_state check (
        (status = 'PENDING' and provider_message_sid is null and error_code is null
            and provider_accepted_at is null)
        or (status = 'PROVIDER_ACCEPTED' and provider_message_sid is not null
            and error_code is null and provider_accepted_at is not null)
        or (status = 'FAILED' and provider_message_sid is null
            and error_code is not null and provider_accepted_at is null)
    )
);

create index idx_outbound_message_user_created
    on outbound_message(user_id, created_at desc);

-- One immutable lookup snapshot has one logical delivery job. Completed jobs stay as
-- the durable idempotency boundary, so a duplicate completion cannot enqueue again.
create unique index uk_background_job_lookup_delivery
    on background_job((payload ->> 'lookupSessionId'))
    where type = 'DELIVER_LOOKUP_RESULT' and payload ? 'lookupSessionId';

-- Deployments may already have unexpired READY results produced by V012-era code.
-- Queue those snapshots once so the feature rollout does not silently strand them.
insert into background_job (
    id, user_id, type, state, payload, attempts, max_attempts, run_after,
    created_at, updated_at
)
select
    gen_random_uuid(), lookup.user_id, 'DELIVER_LOOKUP_RESULT', 'QUEUED',
    jsonb_build_object('schemaVersion', 1, 'lookupSessionId', lookup.id::text),
    0, 3, now(), now(), now()
from lookup_session lookup
where lookup.status = 'READY'
  and lookup.expires_at > now()
  and not exists (
      select 1
      from background_job job
      where job.type = 'DELIVER_LOOKUP_RESULT'
        and job.payload ->> 'lookupSessionId' = lookup.id::text
  );
