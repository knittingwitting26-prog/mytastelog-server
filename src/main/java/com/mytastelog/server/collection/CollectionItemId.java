package com.mytastelog.server.collection;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class CollectionItemId implements Serializable {
	@Column(name = "collection_id", length = 128)
	private String collectionId;

	@Column(name = "item_id", length = 128)
	private String itemId;

	protected CollectionItemId() {
	}

	public CollectionItemId(String collectionId, String itemId) {
		this.collectionId = collectionId;
		this.itemId = itemId;
	}

	public String getCollectionId() { return collectionId; }
	public String getItemId() { return itemId; }

	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (!(object instanceof CollectionItemId other)) return false;
		return Objects.equals(collectionId, other.collectionId) && Objects.equals(itemId, other.itemId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(collectionId, itemId);
	}
}
