# 먹고옴 S3 Photo Backend Foundation v1 구현 보고서

## 1. 기존 Photo 구조

Record와 Wishlist는 `photo_reference varchar(512)` nullable 컬럼을 가지고 있었고, Collection에는 사진 컬럼이 없었다. 기존 값에 legacy/local/mock 문자열이 있을 수 있어 관리 key는 정규식으로 별도 판별한다.

## 2. AWS SDK dependency

`software.amazon.awssdk:s3:2.25.60` 단일 module만 추가했다. AWS SDK 전체 bundle이나 image processing dependency는 추가하지 않았다.

## 3. S3 config와 credential provider

`app.photo.s3.region=${AWS_REGION:}`과 `app.photo.s3.bucket=${MYTASTELOG_S3_BUCKET:}`을 `PhotoS3Properties` 설정으로 binding한다. 둘 중 하나라도 비어 있으면 애플리케이션은 기동하지만 Photo 호출은 `STORAGE_CONFIGURATION_ERROR`(503)를 반환한다.

S3 client는 첫 storage 호출 시 한 번만 생성하여 재사용하고, `DefaultCredentialsProvider` 체인을 명시적으로 사용한다. Production EC2에서는 `mytastelog-ec2-role`, local에서는 AWS 표준 credential chain을 사용한다. access key, secret, token은 코드나 설정에 추가하지 않았다.

## 4. Storage abstraction과 object key

`PhotoStorage` abstraction은 `store`, `read`, `delete`, managed-reference 판별, config 검증을 제공하고 `S3PhotoStorage`가 AWS SDK v2로 구현한다. key는 서버가 `photos/{records|wishlist|collections}/{uuid}.{jpg|png|webp}`로 생성한다. 사용자 filename, place, email, provider subject를 key에 넣지 않는다.

## 5. Validation

JPEG, PNG, WEBP만 허용한다. multipart `Content-Type`과 magic bytes(JPEG SOI, PNG signature, RIFF/WEBP)가 모두 일치해야 한다. 빈 파일과 10MB 초과 파일을 거부한다. Spring multipart 제한은 file 10MB, request 11MB로 설정했고 framework 제한 초과도 413 validation error로 매핑했다. resize, thumbnail, compression, EXIF 처리는 범위에서 제외했다.

## 6. API

- Record: `POST/GET/DELETE /api/v1/records/{id}/photo`
- Wishlist: `POST/GET/DELETE /api/v1/wishlist/{id}/photo`
- Collection: `POST/GET/DELETE /api/v1/collections/{id}/photo`

POST는 `multipart/form-data`의 `file` field를 받고 `hasPhoto` 및 authenticated read endpoint를 반환한다. GET은 session owner 확인 후 S3 content type으로 binary를 streaming response하며 `Cache-Control: no-store`를 사용한다. public URL과 presigned URL은 사용하지 않는다.

## 7. Collection migration

실제 마지막 migration이 V4임을 확인하고 `V5__collection_photo_reference.sql`을 추가했다. 내용은 `collections.photo_reference varchar(512) null`이다. V1~V4는 변경하지 않았다. Collection bootstrap DTO에도 nullable `photo`를 추가했으며 null이면 frontend fallback이 계속 사용될 수 있다.

## 8. Replace/remove/delete semantics

Replace는 새 object upload → entity reference 변경 및 DB flush/commit → 기존 managed object best-effort delete 순서다. upload 실패 시 기존 reference를 유지한다. DB flush 실패 또는 transaction rollback 시 신규 object를 best-effort로 정리한다. 기존 object cleanup 실패는 새 DB reference를 되돌리지 않고 key와 exception type을 error log로 남긴다.

Photo DELETE는 managed reference임과 config를 확인한 다음 DB reference를 제거하고 commit 후 S3 object를 best-effort 정리한다. Record, Wishlist, Collection entity 삭제도 동일하게 commit 후 managed object를 정리한다. URL, blob, local-photo, mock 문자열은 S3 delete 대상이 아니다.

## 9. Wishlist → Record photo transfer

명시적 conversion과 동일 place의 direct Record 생성 모두 Wishlist의 managed S3 key를 Record가 그대로 승계한다. download/re-upload하지 않고, Wishlist 삭제 cleanup이 승계한 object를 제거하지 않는다.

## 10. Authorization과 error handling

모든 Photo API는 HttpSession Authentication → current account ID → entity lookup → owner 검증 순서를 사용한다. request에 ownerId를 받지 않는다. 미인증 401, 다른 owner 403, entity/no managed photo 404, 파일 validation 400/413, config 503 `STORAGE_CONFIGURATION_ERROR`, upload/read 503 `STORAGE_ERROR`로 기존 `ErrorResponse` contract를 재사용한다. AWS exception 상세를 client에 노출하지 않는다.

## 11. Anonymous transfer compatibility

Anonymous IndexedDB/Archive Import는 구현하지 않았다. 향후 consent가 완료된 후 local Blob을 생성된 server entity의 동일 multipart Photo API로 업로드할 수 있다. `local-photo:*`는 managed S3 key로 오인하지 않는다.

## 12. 수정/추가 파일

- `pom.xml`, `application.yaml`
- `V5__collection_photo_reference.sql`
- Record/Wishlist/Collection controller, ArchiveService, CollectionEntity, PlaceArchiveEntity
- Archive response/mapper, global error code/handler
- `photo` package: config, properties, validation, storage abstraction/S3 implementation, application service, response/value types
- Photo validator/storage/service/controller/config-off/failure tests
- migration 기대값을 갱신한 `BackendFoundationIntegrationTest`

## 13. 검증 결과

- Photo/migration 표적 테스트: 17 tests, 0 failures, 0 errors, 0 skipped
- 전체 Backend 테스트: 117 tests, 0 failures, 0 errors, 1 AWS opt-in skipped
- Flyway fresh H2/MySQL mode: V1 → V5 적용 PASS
- JPA `ddl-auto=validate`: PASS
- config-off: 기동 PASS, Photo 호출의 configuration error PASS
- server smoke: test context 및 별도 기동/health smoke PASS

## 14. Real AWS smoke

로컬 환경에 `AWS_REGION`, `MYTASTELOG_S3_BUCKET`, AWS profile/credential file, AWS CLI가 모두 없어 실제 bucket 변경 테스트를 수행하지 않았다. EC2에서 `AWS_REGION=ap-northeast-2`, `MYTASTELOG_S3_BUCKET=mytastelog-photos`를 설정하고 IAM role로 Record/Wishlist/Collection upload-read-replace-delete와 Wishlist conversion 보존을 smoke test해야 READY로 승격할 수 있다.

## 15. 남은 작업

Frontend에 file input, upload progress/error, authenticated image fetch/blob URL lifecycle, fallback 연결을 추가해야 한다. Production에서는 두 환경변수, EC2 role attachment, bucket region/policy, 10MB reverse-proxy limit을 확인해야 한다.

## 16. 판정

**IMPLEMENTATION COMPLETE / REAL AWS SMOKE REQUIRED**
