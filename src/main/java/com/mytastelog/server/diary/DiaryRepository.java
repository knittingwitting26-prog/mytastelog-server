package com.mytastelog.server.diary;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DiaryRepository extends JpaRepository<DiaryEntity, String> {
	Optional<DiaryEntity> findByIdAndOwner_Id(String id, String ownerId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from DiaryEntity d where d.id = :id and d.owner.id = :ownerId")
	Optional<DiaryEntity> findByIdAndOwnerIdForUpdate(@Param("id") String id, @Param("ownerId") String ownerId);
	List<DiaryEntity> findAllByOwner_IdOrderByCreatedAtAsc(String ownerId);
}
