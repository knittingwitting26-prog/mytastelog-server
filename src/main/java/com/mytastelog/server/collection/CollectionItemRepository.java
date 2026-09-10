package com.mytastelog.server.collection;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionItemRepository extends JpaRepository<CollectionItemEntity, CollectionItemId> {
	List<CollectionItemEntity> findByCollection_IdOrderByPosition(String collectionId);
	Optional<CollectionItemEntity> findById_CollectionIdAndId_ItemId(String collectionId, String itemId);
	List<CollectionItemEntity> findById_ItemIdOrderByCollection_IdAsc(String itemId);
}
