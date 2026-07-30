alter table deck
    add column theme_color varchar(24) not null default 'GREEN';

alter table deck
    add constraint ck_deck_theme_color
        check (theme_color in ('GREEN', 'ORANGE', 'BLUE', 'TEAL', 'PURPLE'));
