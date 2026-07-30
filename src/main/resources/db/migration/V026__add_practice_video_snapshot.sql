alter table practice_session_item
    add column video_id varchar(11),
    add column video_start_seconds bigint;

alter table practice_session_item
    add constraint ck_practice_item_video_snapshot check (
        (video_id is null and video_start_seconds is null)
        or (
            video_id ~ '^[A-Za-z0-9_-]{11}$'
            and video_start_seconds is not null
            and video_start_seconds >= 0
        )
    );
