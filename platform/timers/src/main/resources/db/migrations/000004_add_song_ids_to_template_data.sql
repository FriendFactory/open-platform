--liquibase formatted sql

--changeset xxd:000004_add_song_ids_to_template_data
alter table stats.template_data add column song_ids bigint[] default '{}';
