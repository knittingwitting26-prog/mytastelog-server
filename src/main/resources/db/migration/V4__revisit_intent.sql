create table revisit_intents (
    id varchar(128) primary key,
    owner_id varchar(36) not null,
    diary_id varchar(128) not null,
    place_id varchar(255) not null,
    created_at timestamp(6) not null,
    constraint fk_revisit_owner foreign key (owner_id) references accounts(id) on delete cascade,
    constraint fk_revisit_diary foreign key (diary_id) references diaries(id) on delete cascade,
    constraint uk_revisit_owner_diary_place unique (owner_id, diary_id, place_id)
);
create index idx_revisit_owner_created on revisit_intents(owner_id, created_at);
create index idx_revisit_diary_place on revisit_intents(diary_id, place_id);
