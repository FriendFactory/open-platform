--liquibase formatted sql

--changeset xxd:000003_add_comfy_ui_task_start_column
alter table public.comfy_ui_task
    add column if not exists started_at timestamp without time zone;
drop index if exists comfy_ui_task_unfinished_tasks_index;
create index if not exists comfy_ui_task_unfinished_tasks_index on public.comfy_ui_task (server_ip, prompt_id, id) include (duration, workflow, started_at) where finished_at is null;
