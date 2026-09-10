create table accounts (
    id varchar(36) primary key,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create table account_identities (
    id bigint auto_increment primary key,
    account_id varchar(36) not null,
    provider varchar(16) not null,
    provider_subject varchar(255) not null,
    created_at timestamp(6) not null,
    constraint fk_account_identities_account foreign key (account_id) references accounts(id) on delete cascade,
    constraint uk_account_identity_provider_subject unique (provider, provider_subject),
    constraint ck_account_identity_provider check (provider in ('google', 'kakao', 'naver'))
);

create table diaries (
    id varchar(128) primary key,
    owner_id varchar(36) not null,
    name varchar(200) not null,
    theme varchar(16) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_diaries_owner foreign key (owner_id) references accounts(id) on delete cascade,
    constraint uk_diaries_id_owner unique (id, owner_id),
    constraint ck_diaries_theme check (theme in ('notebook', 'travel'))
);
create index idx_diaries_owner on diaries(owner_id);

create table records (
    id varchar(128) primary key,
    owner_id varchar(36) not null,
    diary_id varchar(128) not null,
    place_id varchar(255) not null,
    place_name varchar(300) not null,
    category varchar(100) not null,
    date_display varchar(200) not null,
    memo text not null,
    address varchar(500) not null,
    rating numeric(2, 1),
    menu varchar(300),
    price bigint,
    note text,
    photo_reference varchar(512),
    visibility varchar(16) not null,
    visit_at timestamp(6) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_records_owner foreign key (owner_id) references accounts(id) on delete cascade,
    constraint fk_records_diary_owner foreign key (diary_id, owner_id) references diaries(id, owner_id) on delete cascade,
    constraint ck_records_rating check (rating is null or (rating >= 0 and rating <= 5)),
    constraint ck_records_price check (price is null or price >= 0),
    constraint ck_records_visibility check (visibility in ('private', 'public'))
);
create index idx_records_owner_diary on records(owner_id, diary_id);
create index idx_records_diary_visit on records(diary_id, visit_at);

create table wishlist (
    id varchar(128) primary key,
    owner_id varchar(36) not null,
    diary_id varchar(128) not null,
    place_id varchar(255) not null,
    place_name varchar(300) not null,
    category varchar(100) not null,
    date_display varchar(200) not null,
    memo text not null,
    address varchar(500) not null,
    rating numeric(2, 1),
    menu varchar(300),
    price bigint,
    note text,
    photo_reference varchar(512),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_wishlist_owner foreign key (owner_id) references accounts(id) on delete cascade,
    constraint fk_wishlist_diary_owner foreign key (diary_id, owner_id) references diaries(id, owner_id) on delete cascade,
    constraint ck_wishlist_rating check (rating is null or (rating >= 0 and rating <= 5)),
    constraint ck_wishlist_price check (price is null or price >= 0)
);
create index idx_wishlist_owner_diary on wishlist(owner_id, diary_id);

create table collections (
    id varchar(128) primary key,
    owner_id varchar(36) not null,
    diary_id varchar(128) not null,
    name varchar(200) not null,
    memo text not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_collections_owner foreign key (owner_id) references accounts(id) on delete cascade,
    constraint fk_collections_diary_owner foreign key (diary_id, owner_id) references diaries(id, owner_id) on delete cascade
);
create index idx_collections_owner_diary on collections(owner_id, diary_id);

create table collection_items (
    collection_id varchar(128) not null,
    item_id varchar(128) not null,
    item_type varchar(16) not null,
    position integer not null,
    primary key (collection_id, item_id),
    constraint fk_collection_items_collection foreign key (collection_id) references collections(id) on delete cascade,
    constraint uk_collection_item_position unique (collection_id, position),
    constraint ck_collection_item_type check (item_type in ('record', 'wishlist')),
    constraint ck_collection_item_position check (position >= 0)
);

-- Record/Wishlist use separate tables. Their shared global item ID namespace and
-- collection item owner/Diary checks are enforced transactionally by Archive services.
