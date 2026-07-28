alter table app_user add column interface_language varchar(2);

alter table app_user add constraint ck_app_user_interface_language
    check (interface_language is null or interface_language in ('en', 'es'));
