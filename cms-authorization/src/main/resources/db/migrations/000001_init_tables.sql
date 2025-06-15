--liquibase formatted sql

--changeset xxd:create-tables

create schema if not exists cms;

create table if not exists cms.role
(
    id         serial,
    name       varchar(128)                not null,
    created_at timestamp without time zone NOT NULL default now(),
    primary key (id)
);

create unique index if not exists role_name_index on cms.role (name);

create table if not exists cms.access_scope
(
    id         serial,
    name       varchar(128)                not null,
    created_at timestamp without time zone NOT NULL default now(),
    primary key (id)
);

create unique index if not exists access_scope_name_index on cms.access_scope (name);

create table if not exists cms.role_access_scope
(
    role_id         int                         not null,
    access_scope_id int                         not null,
    created_at      timestamp without time zone NOT NULL default now(),
    primary key (role_id, access_scope_id),
    foreign key (role_id) references cms.role (id),
    foreign key (access_scope_id) references cms.access_scope (id)
);

create table if not exists cms.user_role
(
    email      varchar(128)                not null,
    role_id    int                         not null,
    created_at timestamp without time zone NOT NULL default now(),
    primary key (email, role_id),
    foreign key (role_id) references cms.role (id)
);

insert into cms.role (name)
values ('Admin'),
       ('QA'),
       ('Artist'),
       ('Content'),
       ('Creator'),
       ('Game'),
       ('Product');

insert into cms.access_scope (name)
values ('AssetRead'),
       ('AssetFull'),
       ('VideoModeration'),
       ('Social'),
       ('Seasons'),
       ('Banking'),
       ('CategoriesRead'),
       ('CategoriesFull'),
       ('Settings');

with roles as (select id
               from cms.role
               where name = 'Admin'),
     access_scopes as (select id
                       from cms.access_scope
                       order by id)
insert
into cms.role_access_scope (role_id, access_scope_id)
select *
from roles,
     access_scopes;

with roles as (select id
               from cms.role
               where name = 'QA'),
     access_scopes as (select id
                       from cms.access_scope
                       where name != 'Settings'
                       order by id)
insert
into cms.role_access_scope (role_id, access_scope_id)
select *
from roles,
     access_scopes;

with roles as (select id
               from cms.role
               where name = 'Artist'),
     access_scopes as (select id
                       from cms.access_scope
                       where name like 'Asset%'
                          or name = 'CategoriesRead')
insert
into cms.role_access_scope (role_id, access_scope_id)
select *
from roles,
     access_scopes;

with roles as (select id
               from cms.role
               where name = 'Content'),
     access_scopes as (select id
                       from cms.access_scope
                       where name in ('AssetRead', 'VideoModeration', 'Social', 'CategoriesRead')
                       order by id)
insert
into cms.role_access_scope (role_id, access_scope_id)
select *
from roles,
     access_scopes;

with roles as (select id
               from cms.role
               where name = 'Creator'),
     access_scopes as (select id
                       from cms.access_scope
                       where name in ('AssetRead', 'VideoModeration', 'CategoriesRead')
                       order by id)
insert
into cms.role_access_scope (role_id, access_scope_id)
select *
from roles,
     access_scopes;

with roles as (select id
               from cms.role
               where name = 'Game'),
     access_scopes as (select id
                       from cms.access_scope
                       where name in ('AssetRead', 'Seasons', 'CategoriesRead')
                       order by id)
insert
into cms.role_access_scope (role_id, access_scope_id)
select *
from roles,
     access_scopes;

with roles as (select id
               from cms.role
               where name = 'Product'),
     access_scopes as (select id
                       from cms.access_scope
                       where name in ('AssetRead', 'Seasons', 'Banking', 'CategoriesRead')
                       order by id)
insert
into cms.role_access_scope (role_id, access_scope_id)
select *
from roles,
     access_scopes;

with roles as (select id
               from cms.role
               where name = 'Admin'),
     users as (select email
               from (values ('karen.oliveira@frever.com'), ('viktor.angmo@frever.com')) email(email))
insert
into cms.user_role (email, role_id)
select *
from users,
     roles;
