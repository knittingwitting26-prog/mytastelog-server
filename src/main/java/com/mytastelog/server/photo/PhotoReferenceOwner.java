package com.mytastelog.server.photo;

import com.mytastelog.server.account.AccountEntity;

public interface PhotoReferenceOwner {
	String getId();
	AccountEntity getOwner();
	String getPhotoReference();
	void setPhotoReference(String photoReference);
}
