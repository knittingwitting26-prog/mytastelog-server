package com.mytastelog.server.archive;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalItemIdRepository extends JpaRepository<GlobalItemIdEntity, String> {
}
