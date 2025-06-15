--liquibase formatted sql

--changeset xxd:000005_add_partial_name_to_comfy_ui_task_table
alter table public.comfy_ui_task add column if not exists partial_name text not null default '';