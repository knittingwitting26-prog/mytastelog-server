package com.mytastelog.server.record;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecordRepository extends JpaRepository<RecordEntity, String> {
	Optional<RecordEntity> findByIdAndOwner_Id(String id, String ownerId);
	boolean existsByIdAndOwner_Id(String id, String ownerId);
	List<RecordEntity> findAllByOwner_IdOrderByVisitAtDesc(String ownerId);
	boolean existsByOwner_IdAndDiary_IdAndPlaceId(String ownerId, String diaryId, String placeId);
	List<RecordEntity> findAllByVisibilityOrderByCreatedAtDesc(RecordVisibility visibility, Pageable pageable);

	@Query("""
		select record from RecordEntity record
		where record.visibility = :visibility
		and record.latitude between :south and :north
		and record.longitude between :west and :east
		order by record.createdAt desc
		""")
	List<RecordEntity> findPublicInBounds(@Param("visibility") RecordVisibility visibility,
		@Param("north") java.math.BigDecimal north, @Param("south") java.math.BigDecimal south,
		@Param("east") java.math.BigDecimal east, @Param("west") java.math.BigDecimal west,
		Pageable pageable);
}
