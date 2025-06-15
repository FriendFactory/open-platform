--liquibase formatted sql

--changeset xxd:000008_template_ranking
create table if not exists stats.template_ranking
(
    template_id bigint not null,
    rank        bigint not null,
    primary key (template_id)
);

create index if not exists template_ranking_rank_idx on stats.template_ranking (rank);
