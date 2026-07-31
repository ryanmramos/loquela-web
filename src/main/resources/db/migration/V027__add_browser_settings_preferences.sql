alter table app_user
    add column automatic_caption_fallback boolean not null default true,
    add column export_deck_prefix varchar(100) not null default 'Loquela — ',
    add column export_include_provenance boolean not null default true;

alter table app_user add constraint ck_app_user_export_deck_prefix_length
    check (char_length(export_deck_prefix) <= 100);
