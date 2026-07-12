create table inbound_message (
    id uuid primary key,
    twilio_message_sid varchar(64) not null,
    user_id uuid not null,
    body_ciphertext bytea,
    media_url_ciphertext bytea,
    media_content_type varchar(255),
    command_type varchar(32),
    received_at timestamptz not null,
    purge_after timestamptz,
    constraint uk_inbound_message_twilio_sid unique (twilio_message_sid),
    constraint fk_inbound_message_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint ck_inbound_message_command_type check (
        command_type is null or command_type in (
            'HELP', 'SETTINGS', 'LANGUAGE', 'ADD', 'LOOKUP', 'SAVE',
            'SOURCES', 'DELETE_SOURCE', 'EXPORT', 'STATUS', 'DELETE_MY_DATA', 'DOCUMENT', 'UNKNOWN'
        )
    )
);

create index idx_inbound_message_user_received
    on inbound_message(user_id, received_at desc);

