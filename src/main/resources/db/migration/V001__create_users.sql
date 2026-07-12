create table app_user (
    id uuid primary key,
    whatsapp_address_hash bytea not null,
    whatsapp_address_ciphertext bytea not null,
    source_language varchar(35) not null,
    definition_language varchar(35) not null,
    status varchar(24) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_app_user_whatsapp_address_hash unique (whatsapp_address_hash),
    constraint ck_app_user_status check (status in ('ACTIVE', 'DISABLED', 'DELETING'))
);

