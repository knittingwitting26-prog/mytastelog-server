package com.mytastelog.server.photo;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PreDestroy;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3PhotoStorage implements PhotoStorage {
	private static final Pattern MANAGED_KEY = Pattern.compile(
		"photos/(records|record-menus|wishlist|collections)/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(jpg|png|webp)");

	private final PhotoS3Properties properties;
	private volatile S3Client client;

	@Autowired
	public S3PhotoStorage(PhotoS3Properties properties) {
		this.properties = properties;
	}

	S3PhotoStorage(PhotoS3Properties properties, S3Client client) {
		this.properties = properties;
		this.client = client;
	}

	@Override
	public void assertConfigured() {
		if (!StringUtils.hasText(properties.region()) || !StringUtils.hasText(properties.bucket())) throw notConfigured();
	}

	@Override
	public String store(PhotoEntityType entityType, PhotoContent content) {
		String key = "photos/" + entityType.path() + "/" + UUID.randomUUID() + extension(content.contentType());
		try {
			client().putObject(PutObjectRequest.builder().bucket(bucket()).key(key)
				.contentType(content.contentType()).contentLength((long) content.bytes().length).build(),
				RequestBody.fromBytes(content.bytes()));
			return key;
		} catch (PhotoStorageException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new PhotoStorageException(PhotoStorageException.Operation.UPLOAD,
				"사진 저장소 업로드에 실패했습니다.", exception);
		}
	}

	@Override
	public PhotoContent read(String objectKey) {
		ensureManaged(objectKey);
		try {
			var response = client().getObject(GetObjectRequest.builder().bucket(bucket()).key(objectKey).build(),
				ResponseTransformer.toBytes());
			return new PhotoContent(response.asByteArray(), response.response().contentType());
		} catch (PhotoStorageException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new PhotoStorageException(PhotoStorageException.Operation.READ,
				"사진 저장소 읽기에 실패했습니다.", exception);
		}
	}

	@Override
	public void delete(String objectKey) {
		ensureManaged(objectKey);
		try {
			client().deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(objectKey).build());
		} catch (PhotoStorageException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new PhotoStorageException(PhotoStorageException.Operation.DELETE,
				"사진 저장소 정리에 실패했습니다.", exception);
		}
	}

	@Override
	public boolean isManagedReference(String reference) {
		return reference != null && MANAGED_KEY.matcher(reference).matches();
	}

	private String extension(String contentType) {
		return switch (contentType) {
			case "image/jpeg" -> ".jpg";
			case "image/png" -> ".png";
			case "image/webp" -> ".webp";
			default -> throw new IllegalArgumentException("Unsupported validated content type: " + contentType);
		};
	}

	private void ensureManaged(String key) {
		if (!isManagedReference(key)) throw new IllegalArgumentException("Unmanaged photo reference");
	}

	private String bucket() {
		if (!StringUtils.hasText(properties.bucket())) throw notConfigured();
		return properties.bucket();
	}

	private S3Client client() {
		assertConfigured();
		S3Client value = client;
		if (value == null) {
			synchronized (this) {
				value = client;
				if (value == null) {
					value = S3Client.builder().region(Region.of(properties.region()))
						.credentialsProvider(DefaultCredentialsProvider.create()).build();
					client = value;
				}
			}
		}
		return value;
	}

	private PhotoStorageException notConfigured() {
		return new PhotoStorageException(PhotoStorageException.Operation.CONFIGURATION,
			"사진 저장소 region 또는 bucket이 설정되지 않았습니다.");
	}

	@PreDestroy
	void closeClient() {
		S3Client value = client;
		if (value != null) value.close();
	}
}
