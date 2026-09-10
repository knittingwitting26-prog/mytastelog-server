create table archive_item_ids (
    id varchar(128) primary key,
    owner_id varchar(36) not null,
    item_type varchar(16) not null,
    created_at timestamp(6) not null,
    constraint fk_archive_item_ids_owner foreign key (owner_id) references accounts(id) on delete cascade,
    constraint ck_archive_item_ids_type check (item_type in ('record', 'wishlist'))
);
insert into archive_item_ids(id, owner_id, item_type, created_at)
select id, owner_id, 'record', created_at from records;
insert into archive_item_ids(id, owner_id, item_type, created_at)
select id, owner_id, 'wishlist', created_at from wishlist;

-- V1 and V2 form the first production bootstrap and run together on an empty
-- mytastelog database, so there are no pre-existing collections to backfill.
alter table collections add column position integer default 0 not null;
alter table collections add constraint ck_collections_position check (position >= 0);
alter table collections add constraint uk_collections_diary_position unique (diary_id, position);
create index idx_collections_diary_position on collections(diary_id, position);
