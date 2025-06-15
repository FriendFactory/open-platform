--liquibase formatted sql

--changeset xxd:000007_follow_edge_index
create index if not exists idx_follow_edge_destination_source on stats.follow_edge (destination, source);
