--liquibase formatted sql

--changeset xxd:000002_video_kpi
create table if not exists stats.follower_stats
(
    group_id        bigint  not null,
    following_count bigint  not null default 0,
    followers_count bigint  not null default 0,
    friends_count   bigint  not null default 0,
    deleted         boolean not null default false,
    primary key (group_id)
);
