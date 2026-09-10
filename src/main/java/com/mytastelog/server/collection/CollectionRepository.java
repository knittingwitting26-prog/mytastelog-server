package com.mytastelog.server.collection;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRepository extends JpaRepository<CollectionEntity, String> {
	Optional<CollectionEntity> findByIdAndOwner_Id(String id, String ownerId);
	List<CollectionEntity> findAllByOwner_IdOrderByDiary_IdAscPositionAsc(String ownerId);
	List<CollectionEntity> findAllByDiary_IdAndOwner_IdOrderByPositionAsc(String diaryId, String ownerId);
	Optional<CollectionEntity> findFirstByDiary_IdAndOwner_IdOrderByPositionDesc(String diaryId, String ownerId);
}
