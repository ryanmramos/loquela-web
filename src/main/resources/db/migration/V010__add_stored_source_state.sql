alter table source_document
    drop constraint ck_source_document_status;

alter table source_document
    add constraint ck_source_document_status check (
        status in (
            'RECEIVED', 'FETCHING', 'STORED', 'EXTRACTING', 'INDEXING',
            'READY', 'FAILED', 'DELETING', 'DELETED'
        )
    );
