create table archive_import_items (
    id bigint auto_increment primary key,
    account_id varchar(36) not null,
    transfer_id varchar(128) not null,
    source_entity varchar(32) not null,
    source_local_id varchar(512) not null,
    server_id varchar(128),
    status varchar(32) not null,
    created_at timestamp(6) not null,
    constraint fk_archive_import_items_account foreign key (account_id) references accounts(id) on delete cascade,
    constraint uk_archive_import_source unique (account_id, transfer_id, source_entity, source_local_id),
    constraint ck_archive_import_status check (status in ('IMPORTED', 'SKIPPED_BY_DOMAIN_RULE'))
);
create index idx_archive_import_transfer on archive_import_items(account_id, transfer_id);
