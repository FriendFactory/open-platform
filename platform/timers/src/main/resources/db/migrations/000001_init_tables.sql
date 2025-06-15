--liquibase formatted sql

--changeset xxd:000001_init_tables
create schema if not exists stats;

create table if not exists stats.template_data
(
    id                             bigint                   not null,
    level_id                       bigint                   not null,
    files                          jsonb                    not null,
    template_sub_category_id       bigint                   not null,
    template_category_id           bigint                   not null,
    title                          text                     not null,
    top_list_position_in_discovery bigint,
    trending_sorting_order         bigint,
    category_sorting_order         bigint,
    sub_category_sorting_order     bigint,
    onboarding_sorting_order       bigint,
    description                    text,
    character_count                integer                  not null,
    artist_name                    text,
    song_name                      text,
    is_deleted                     bool                     not null,
    reverse_thumbnail              bool                     not null,
    readiness_id                   bigint                   not null,
    tags                           bigint[]                 not null,
    usage_count                    bigint                   not null default 0,
    created_time                   timestamp with time zone          default now(),
    original_video_id              bigint,
    creator_id                     bigint                   not null,
    creator_nickname               text                     not null,
    creator_main_character_id      bigint,
    creator_main_character_files   text,
    country                        text,
    language                       text,
    external_song_ids              bigint[]                 not null default '{}',
    -- aggregation related fields
    event_id                       bigint                   not null,
    stats_updated_timestamp        timestamp with time zone not null,
    primary key (id)
);

create index if not exists template_data_group_id_idx
    on stats.template_data (creator_id);

create index if not exists template_data_event_id_idx
    on stats.template_data (event_id);

create table if not exists stats.timer_execution
(
    timer_name          text                     not null,
    last_execution_time timestamp with time zone not null,
    primary key (timer_name)
);

create table if not exists stats.video_template_map
(
    video_id         bigint                   not null,
    template_id      bigint                   not null,
    mapped_timestamp timestamp with time zone not null,
    primary key (video_id, template_id)
);

create index if not exists video_template_map_template_id_idx
    on stats.video_template_map (template_id);

create index if not exists video_template_map_mapped_timestamp_idx
    on stats.video_template_map (mapped_timestamp);
