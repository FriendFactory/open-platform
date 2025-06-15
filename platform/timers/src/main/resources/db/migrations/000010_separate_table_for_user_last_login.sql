--liquibase formatted sql

--changeset xxd:000010_separate_table_for_user_last_login
create table if not exists stats.user_extra_info
(
    group_id   bigint                   not null,
    last_login timestamp with time zone not null default now(),
    primary key (group_id)
);

create index if not exists idx_user_last_login_user_extra_info on stats.user_extra_info (last_login);