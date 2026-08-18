package me.nawa.common.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.annotation.PreDestroy;
import java.util.UUID;

/**
 * 영수증 이미지 저장소
 *
 * 정산 영수증 이미지를 S3에 올리고 다시 꺼냅니다
 * 버킷은 백엔드 AWS 계정이 아닌 별도 계정이 소유합니다
 * EC2 인스턴스 역할 대신 버킷 소유 계정이 발급한 IAM 사용자 키를 환경변수로 받아 씁니다
 */
@Component
public class ReceiptStorageService {

    /**
     * 영수증 객체 키 접두사
     *
     * IAM 정책이 {@code arn:aws:s3:::<bucket>/receipts/*}로 좁혀진 상태입니다
     * 이 접두사를 벗어난 키는 정책 단계에서 막히므로 런타임에 AccessDenied가 납니다
     */
    private static final String KEY_PREFIX = "receipts/";

    private final S3Properties properties;
    private final S3Client client;

    public ReceiptStorageService(S3Properties properties) {
        this.properties = properties;
        this.client = S3Client.builder()
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(credentialsProvider(properties))
            .build();
    }

    /**
     * 자격증명 공급자 결정
     *
     * 키가 비어 있으면 SDK 기본 자격증명 체인(환경변수, ~/.aws/credentials, 인스턴스 역할)에 맡깁니다
     * AwsBasicCredentials가 빈 문자열을 거부함으로, 로컬에서 S3 설정 없이 컨텍스트 띄우려면 이 분기가 필요합니다
     */
    private static AwsCredentialsProvider credentialsProvider(S3Properties properties) {
        if (properties.getAccessKeyId().isBlank() || properties.getSecretAccessKey().isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey()));
    }

    @PreDestroy
    void shutdown() {
        client.close();
    }

    /**
     * 영수증 업로드
     *
     * 이미지를 올리고 DB에 저장할 객체 키를 반환합니다
     * MIME 타입과 크기는 호출 쪽에서 먼저 검증한 뒤 호출합니다
     */
    public String upload(long settlementId, String extension, String contentType, byte[] content) {
        String objectKey = KEY_PREFIX + settlementId + "/" + UUID.randomUUID() + "." + extension;

        client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.getReceiptBucket())
                .key(objectKey)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(content));

        return objectKey;
    }

    /**
     * 영수증 조회
     *
     * 객체 키가 가리키는 이미지의 본문과 MIME 타입을 내려받습니다
     */
    public StoredReceipt download(String objectKey) {
        requireReceiptKey(objectKey);

        ResponseBytes<GetObjectResponse> object = client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(properties.getReceiptBucket())
                .key(objectKey)
                .build());

        return new StoredReceipt(object.asByteArray(), object.response().contentType());
    }

    /**
     * 영수증 삭제
     *
     * 객체 키가 가리키는 이미지를 버킷에서 제거합니다
     */
    public void delete(String objectKey) {
        requireReceiptKey(objectKey);

        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.getReceiptBucket())
                .key(objectKey)
                .build());
    }

    /**
     * 객체 키 검증
     *
     * 객체 키는 DB에서 읽어온 값이라 코드가 만든 값이라는 보장이 없습니다
     * 키가 그대로 S3 호출로 새어나가지 않게 여기서 막습니다
     */
    private static void requireReceiptKey(String objectKey) {
        if (objectKey == null || !objectKey.startsWith(KEY_PREFIX)) {
            throw new IllegalArgumentException("영수증 객체 키는 " + KEY_PREFIX + "로 시작해야 합니다.");
        }
    }
}
