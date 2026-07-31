alter table lookup_session
    drop constraint ck_lookup_session_error_state;

-- READY snapshots can retain either a dictionary definition or corpus contexts when
-- the other lookup stage has no match. FAILED remains reserved for no usable result.
alter table lookup_session
    add constraint ck_lookup_session_error_state check (
        (status = 'FAILED' and error_code is not null)
        or (status = 'READY' and (error_code is null or error_code in ('DEFINITION_NOT_FOUND', 'CONTEXT_NOT_FOUND')))
        or (status not in ('FAILED', 'READY') and error_code is null)
    );
