--liquibase formatted sql

--changeset xxd:000004_add_comfy_ui_autoscaling_setup
create table if not exists public.comfyui_autoscaling_check
(
    setup_type         text not null,
    checked_at         timestamp without time zone default now(),
    above_threshold_since timestamp without time zone,
    below_threshold_since timestamp without time zone
);

create unique index if not exists comfyui_autoscaling_check_setup_type_index
    on public.comfyui_autoscaling_check (setup_type);

insert into public.comfyui_autoscaling_check (setup_type)
values ('LipSync'),
       ('Pulid'),
       ('Makeup');