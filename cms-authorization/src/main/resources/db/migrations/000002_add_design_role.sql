--liquibase formatted sql

--changeset xxd:add-design-role
insert into cms.role (name)
values ('Design')
ON CONFLICT (name) DO NOTHING;

with roles as (select id
               from cms.role
               where name = 'Design'),
     access_scopes as (select id
                       from cms.access_scope
                       where name like 'Asset%'
                          or name = 'CategoriesRead')
insert
into cms.role_access_scope (role_id, access_scope_id)
select *
from roles,
     access_scopes
ON CONFLICT ON CONSTRAINT role_access_scope_pkey DO NOTHING;
