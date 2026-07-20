alter table card
    add column saved_from_lookup_session_id uuid,
    add column saved_from_context_number integer,
    add constraint ck_card_saved_lookup_origin check (
        (saved_from_lookup_session_id is null and saved_from_context_number is null)
        or (saved_from_lookup_session_id is not null and saved_from_context_number > 0)
    );

-- The origin is intentionally not a foreign key: lookup sessions expire and may be
-- purged, while cards must retain their copied definition, context, and attribution.
create unique index uk_card_user_saved_lookup_context
    on card(user_id, saved_from_lookup_session_id, saved_from_context_number)
    where saved_from_lookup_session_id is not null;
