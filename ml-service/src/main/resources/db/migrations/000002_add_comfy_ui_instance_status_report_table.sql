--liquibase formatted sql

--changeset xxd:000002_add_comfy_ui_instance_status_report_table
create table if not exists public.comfy_ui_instance_status_report
(
    id                  bigint generated always as identity primary key,
    server_ip           inet    not null,
    zero_tasks_reported boolean not null            default false,
    status_reported_at  timestamp without time zone default now(),
    unique (server_ip)
);
