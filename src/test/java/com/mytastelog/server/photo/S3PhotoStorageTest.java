package com.mytastelog.server.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3PhotoStorageTest {
	@Test
	void uploadsReadsAndDeletesWithOpaqueManagedKeyAndMetadata() {
		S3Client client = mock(S3Client.class);
		when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());
		when(client.getObject(any(GetObjectRequest.class), anyResponseTransformer())).thenReturn(
			ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("image/png").build(), new byte[] {1, 2}));
		when(client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());
		S3PhotoStorage storage = new S3PhotoStorage(new PhotoS3Properties("ap-northeast-2", "bucket"), client);

		String key = storage.store(PhotoEntityType.RECORD, new PhotoContent(new byte[] {1, 2}, "image/png"));

		assertThat(key).matches("photos/records/[0-9a-f-]{36}\\.png");
		assertThat(storage.isManagedReference(key)).isTrue();
		assertThat(storage.read(key).contentType()).isEqualTo("image/png");
		storage.delete(key);
		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(client).putObject(request.capture(), any(RequestBody.class));
		assertThat(request.getValue().bucket()).isEqualTo("bucket");
		assertThat(request.getValue().contentType()).isEqualTo("image/png");
		verify(client).deleteObject(any(DeleteObjectRequest.class));
	}

	@Test
	void recognizesOnlyServerManagedKeysAndFailsClearlyWhenConfigIsOff() {
		S3PhotoStorage storage = new S3PhotoStorage(new PhotoS3Properties("", ""));
		assertThat(storage.isManagedReference("photos/wishlist/550e8400-e29b-41d4-a716-446655440000.webp")).isTrue();
		assertThat(storage.isManagedReference("https://example.com/photo.jpg")).isFalse();
		assertThat(storage.isManagedReference("local-photo:1")).isFalse();
		assertThat(storage.isManagedReference("photos/records/not-a-uuid.jpg")).isFalse();
		assertThatThrownBy(storage::assertConfigured).isInstanceOfSatisfying(PhotoStorageException.class,
			exception -> assertThat(exception.operation()).isEqualTo(PhotoStorageException.Operation.CONFIGURATION));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>> anyResponseTransformer() {
		return (ResponseTransformer) any(ResponseTransformer.class);
	}
}
