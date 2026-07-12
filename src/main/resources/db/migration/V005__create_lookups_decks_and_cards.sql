create table lookup_session (
    id uuid primary key,
    user_id uuid not null,
    requested_term varchar(256) not null,
    normalized_term varchar(256) not null,
    source_language varchar(35) not null,
    definition_language varchar(35) not null,
    result_payload jsonb not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    constraint fk_lookup_session_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint ck_lookup_session_expiry check (expires_at > created_at)
);

create index idx_lookup_session_user_created
    on lookup_session(user_id, created_at desc);

create table deck (
    id uuid primary key,
    user_id uuid not null,
    name varchar(255) not null,
    normalized_name varchar(255) not null,
    status varchar(24) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_deck_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint uk_deck_user_normalized_name unique (user_id, normalized_name),
    constraint uk_deck_id_user unique (id, user_id),
    constraint ck_deck_status check (status in ('ACTIVE', 'ARCHIVED', 'DELETING'))
);

create table card (
    id uuid primary key,
    user_id uuid not null,
    deck_id uuid not null,
    note_type varchar(64) not null,
    source_language varchar(35) not null,
    definition_language varchar(35) not null,
    term text not null,
    definition text not null,
    example_text text,
    source_id uuid,
    source_location jsonb,
    payload jsonb not null default '{}'::jsonb,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_card_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint fk_card_deck_owner foreign key (deck_id, user_id)
        references deck(id, user_id) on delete cascade,
    constraint fk_card_source foreign key (source_id)
        references source_document(id) on delete set null,
    constraint ck_card_note_type check (note_type in ('BASIC', 'BASIC_CONTEXT')),
    constraint ck_card_version check (version >= 0)
);

create index idx_card_user_deck_created on card(user_id, deck_id, created_at);
create index idx_card_source on card(source_id) where source_id is not null;

