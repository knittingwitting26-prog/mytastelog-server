package com.mytastelog.server.revisit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisitIntentRepository extends JpaRepository<RevisitIntentEntity, String> {
	Optional<RevisitIntentEntity> findByIdAndOwner_Id(String id, String ownerId);
	Optional<RevisitIntentEntity> findByOwner_IdAndDiary_IdAndPlaceId(String ownerId, String diaryId, String placeId);
	List<RevisitIntentEntity> findAllByOwner_IdOrderByCreatedAtAsc(String ownerId);
	void deleteAllByOwner_IdAndDiary_IdAndPlaceId(String ownerId, String diaryId, String placeId);
}
