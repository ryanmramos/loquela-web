create table user_identity (
    id uuid primary key,
    user_id uuid not null,
    provider varchar(24) not null,
    provider_subject_hash bytea not null,
    provider_subject_ciphertext bytea,
    display_email varchar(320),
    display_name varchar(200),
    verified_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_user_identity_user foreign key (user_id)
        references app_user(id) on delete cascade,
    constraint uk_user_identity_provider_subject unique (provider, provider_subject_hash),
    constraint uk_user_identity_user_provider unique (user_id, provider),
    constraint ck_user_identity_provider check (provider in ('GOOGLE', 'WHATSAPP')),
    constraint ck_user_identity_whatsapp_ciphertext check (
        provider <> 'WHATSAPP' or provider_subject_ciphertext is not null
    )
);

insert into user_identity (
    id, user_id, provider, provider_subject_hash, provider_subject_ciphertext,
    verified_at, created_at, updated_at
)
select
    gen_random_uuid(), id, 'WHATSAPP', whatsapp_address_hash, whatsapp_address_ciphertext,
    created_at, created_at, updated_at
from app_user;

create index idx_user_identity_user_provider
    on user_identity(user_id, provider);

alter table app_user
    drop constraint uk_app_user_whatsapp_address_hash,
    drop column whatsapp_address_hash,
    drop column whatsapp_address_ciphertext;
