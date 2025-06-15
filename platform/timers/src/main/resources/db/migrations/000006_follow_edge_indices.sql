--liquibase formatted sql

--changeset xxd:000006_follow_edge_indices
create index if not exists idx_follow_edge_destination on stats.follow_edge (destination, source) 
    where not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules)) and destination != 742;
create index if not exists idx_follow_edge_destination_login on stats.follow_edge (destination, destination_latest_login, source)
    where not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules)) and destination != 742;
create index if not exists idx_follow_edge_for_hops on stats.follow_edge (source, destination_latest_login, destination)
    where not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules)) and destination != 742;
create index if not exists idx_follow_edge_source_latest_login on stats.follow_edge (source_latest_login, source);
create index if not exists idx_follow_edge_destination_latest_login on stats.follow_edge (destination_latest_login, destination);
