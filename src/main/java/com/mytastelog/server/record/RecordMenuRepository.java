package com.mytastelog.server.record;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordMenuRepository extends JpaRepository<RecordMenuEntity, String> {
	List<RecordMenuEntity> findByRecord_IdOrderByPositionAsc(String recordId);
}
