-- Cards retain the exact template contract used at save time. This lets future
-- export jobs reject unknown templates without reconstructing user-facing fields.
alter table card
    add column anki_template_version varchar(64);

create index idx_card_user_anki_template
    on card(user_id, anki_template_version)
    where anki_template_version is not null;
