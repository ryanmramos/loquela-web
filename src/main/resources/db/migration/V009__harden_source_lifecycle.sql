alter table source_document
    add column version bigint not null default 0,
    add constraint ck_source_document_version check (version >= 0);

create index idx_background_job_source_reference
    on background_job(user_id, (payload ->> 'sourceId'), state)
    where payload ? 'sourceId';
