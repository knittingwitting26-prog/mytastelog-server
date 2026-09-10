package com.mytastelog.server.photo;

public interface PhotoStorage {
	void assertConfigured();
	String store(PhotoEntityType entityType, PhotoContent content);
	PhotoContent read(String objectKey);
	void delete(String objectKey);
	boolean isManagedReference(String reference);
}
