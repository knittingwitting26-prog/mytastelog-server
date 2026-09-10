package com.mytastelog.server.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "collection_items", uniqueConstraints =
	@UniqueConstraint(name = "uk_collection_item_position", columnNames = {"collection_id", "position"}))
public class CollectionItemEntity {
	@EmbeddedId
	private CollectionItemId id;

	@MapsId("collectionId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_id", nullable = false)
	private CollectionEntity collection;

	@Convert(converter = ArchiveItemTypeConverter.class)
	@Column(name = "item_type", nullable = false, length = 16)
	private ArchiveItemType itemType;

	@Column(nullable = false)
	private int position;

	protected CollectionItemEntity() {
	}

	public CollectionItemEntity(CollectionEntity collection, String itemId, ArchiveItemType itemType, int position) {
		this.id = new CollectionItemId(collection.getId(), itemId);
		this.collection = collection;
		this.itemType = itemType;
		this.position = position;
	}

	public void moveTo(int position) { this.position = position; }

	public CollectionItemId getId() { return id; }
	public CollectionEntity getCollection() { return collection; }
	public ArchiveItemType getItemType() { return itemType; }
	public int getPosition() { return position; }
}
