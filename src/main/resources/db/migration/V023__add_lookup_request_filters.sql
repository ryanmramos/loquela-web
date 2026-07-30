alter table lookup_session
    add column request_filters jsonb not null default '{}'::jsonb;
