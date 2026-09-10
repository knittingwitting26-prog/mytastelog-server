package com.mytastelog.server.record;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordRepository extends JpaRepository<RecordEntity, String> {
	Optional<RecordEntity> findByIdAndOwner_Id(String id, String ownerId);
	boolean existsByIdAndOwner_Id(String id, String ownerId);
	List<RecordEntity> findAllByOwner_IdOrderByCreatedAtAsc(String ownerId);
	boolean existsByOwner_IdAndDiary_IdAndPlaceId(String ownerId, String diaryId, String placeId);
}
