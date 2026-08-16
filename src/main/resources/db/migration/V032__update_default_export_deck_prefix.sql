alter table app_user
    alter column export_deck_prefix set default 'Loquela::';

update app_user
set export_deck_prefix = 'Loquela::'
where export_deck_prefix = 'Loquela — ';
