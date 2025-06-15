--liquibase formatted sql

--changeset xxd:000005_add_follow_edge
create table if not exists stats.follow_edge
(
    source                         bigint                   not null,
    destination                    bigint                   not null,
    is_mutual                      boolean                  not null default false,
    source_latest_login            timestamp with time zone not null default now(),
    destination_latest_login       timestamp with time zone not null default now(),
    source_is_minor                boolean                  not null default false,
    source_strict_coppa_rules      boolean                           default false,
    destination_is_minor           boolean                  not null default false,
    destination_strict_coppa_rules boolean                           default false,
    primary key (source, destination)
);

create index if not exists idx_user_login_groupId_user_activity on "UserActivity" ("GroupId", "OccurredAt") where "ActionType" = 'Login'::"UserActionType";

create index if not exists idx_user_login_time_user_activity on "UserActivity" ("OccurredAt", "GroupId") where "ActionType" = 'Login'::"UserActionType";
