package com.mytastelog.server.wishlist;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<WishlistEntity, String> {
	Optional<WishlistEntity> findByIdAndOwner_Id(String id, String ownerId);
	boolean existsByIdAndOwner_Id(String id, String ownerId);
	List<WishlistEntity> findAllByOwner_IdOrderByCreatedAtAsc(String ownerId);
	boolean existsByOwner_IdAndDiary_IdAndPlaceId(String ownerId, String diaryId, String placeId);
}
