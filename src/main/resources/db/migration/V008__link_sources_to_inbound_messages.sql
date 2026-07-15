alter table inbound_message
    add constraint uk_inbound_message_id_user unique (id, user_id);

alter table source_document
    add column inbound_message_id uuid,
    add constraint uk_source_document_inbound_message unique (inbound_message_id),
    add constraint fk_source_document_inbound_owner
        foreign key (inbound_message_id, user_id)
        references inbound_message(id, user_id)
        on delete set null (inbound_message_id);

create index idx_inbound_message_retained_content_expiry
    on inbound_message(purge_after)
    where body_ciphertext is not null or media_url_ciphertext is not null;
