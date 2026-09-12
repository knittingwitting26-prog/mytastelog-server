package com.mytastelog.server.archive;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveImportItemRepository extends JpaRepository<ArchiveImportItemEntity, Long> {
	Optional<ArchiveImportItemEntity> findByAccountIdAndTransferIdAndSourceEntityAndSourceLocalId(
		String accountId, String transferId, String sourceEntity, String sourceLocalId);
}
