--liquibase formatted sql

--changeset xxd:000002_video_kpi
create table if not exists stats.video_kpi
(
    video_id     bigint  not null,
    likes        bigint  not null default 0,
    views        bigint  not null default 0,
    comments     bigint  not null default 0,
    shares       bigint  not null default 0,
    remixes      bigint  not null default 0,
    battles_won  bigint  not null default 0,
    battles_lost bigint  not null default 0,
    deleted      boolean not null default false,
    primary key (video_id)
);


