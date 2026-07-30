create table practice_session (
    id uuid primary key,
    user_id uuid not null,
    deck_id uuid not null,
    deck_name varchar(255) not null,
    status varchar(24) not null,
    current_position integer not null default 0,
    answer_revealed boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    expires_at timestamptz not null,
    completed_at timestamptz,
    constraint fk_practice_session_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint fk_practice_session_deck_owner foreign key (deck_id, user_id)
        references deck(id, user_id) on delete cascade,
    constraint uk_practice_session_id_user unique (id, user_id),
    constraint ck_practice_session_status
        check (status in ('ACTIVE', 'COMPLETED', 'EXPIRED')),
    constraint ck_practice_session_position check (current_position >= 0),
    constraint ck_practice_session_expiry check (expires_at > created_at),
    constraint ck_practice_session_completion check (
        (status = 'COMPLETED' and completed_at is not null)
        or (status <> 'COMPLETED' and completed_at is null)
    )
);

create unique index uk_practice_session_user_active
    on practice_session(user_id) where status = 'ACTIVE';

create index idx_practice_session_user_updated
    on practice_session(user_id, updated_at desc);

create table practice_session_item (
    id uuid primary key,
    session_id uuid not null,
    user_id uuid not null,
    card_id uuid not null,
    position integer not null,
    note_type varchar(64) not null,
    term text not null,
    definition text not null,
    example_text text,
    source_name text,
    source_locator varchar(128),
    constraint fk_practice_item_session_owner foreign key (session_id, user_id)
        references practice_session(id, user_id) on delete cascade,
    constraint uk_practice_item_position unique (session_id, position),
    constraint ck_practice_item_position check (position >= 0),
    constraint ck_practice_item_note_type check (note_type in ('BASIC', 'BASIC_CONTEXT'))
);

create index idx_practice_item_session_position
    on practice_session_item(session_id, position);

create table practice_response (
    id uuid primary key,
    session_id uuid not null,
    user_id uuid not null,
    card_id uuid not null,
    position integer not null,
    request_id uuid not null,
    rating varchar(24) not null,
    created_at timestamptz not null,
    constraint fk_practice_response_session_owner foreign key (session_id, user_id)
        references practice_session(id, user_id) on delete cascade,
    constraint uk_practice_response_request unique (session_id, request_id),
    constraint uk_practice_response_position unique (session_id, position),
    constraint ck_practice_response_position check (position >= 0),
    constraint ck_practice_response_rating
        check (rating in ('NEEDS_WORK', 'ALMOST', 'GOT_IT', 'EASY'))
);

create index idx_practice_response_session_created
    on practice_response(session_id, created_at);
