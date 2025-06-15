--liquibase formatted sql

--changeset xxd:000009_influential_nodes
create table if not exists stats.influential_nodes
(
    destination bigint not null,
    rank        bigint not null,
    primary key (destination)
);

create index if not exists influential_nodes_rank_idx on stats.influential_nodes (rank);
