alter table source_document
    add column staged_text_ciphertext bytea;

alter table card
    add column last_practiced_at timestamptz;

create index idx_card_user_practice
    on card(user_id, last_practiced_at);

create index idx_source_document_user_status
    on source_document(user_id, status);

create index idx_background_job_user_state
    on background_job(user_id, state);
