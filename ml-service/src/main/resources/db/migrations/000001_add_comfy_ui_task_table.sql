--liquibase formatted sql

--changeset xxd:000001_add_comfy_ui_task_table
create table if not exists public.comfy_ui_task
(
    id          bigint generated always as identity primary key,
    prompt_id   uuid    not null,
    server_ip   inet    not null,
    group_id    bigint  not null,
    video_id    bigint,
    level_id    bigint,
    version     text,
    file_name   text    not null,
    workflow    text    not null,
    duration    integer not null            default 0,
    priority    integer not null            default 0,
    created_at  timestamp without time zone default now(),
    finished_at timestamp without time zone
);

create index if not exists comfy_ui_task_prompt_id_index on public.comfy_ui_task (prompt_id);
create index if not exists comfy_ui_task_unfinished_tasks_index on public.comfy_ui_task (server_ip, prompt_id, id) include (duration, workflow) where finished_at is null;
create index if not exists comfy_ui_task_finished_tasks_index on public.comfy_ui_task (server_ip) where finished_at is not null;
