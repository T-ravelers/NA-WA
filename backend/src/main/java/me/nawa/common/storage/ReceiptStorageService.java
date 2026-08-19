package me.nawa.common.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ReceiptStorageService {

    private static final String KEY_PREFIX = "receipts/";

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("[a-z0-9]{1,10}");

    private final S3Properties properties;
    private final S3Client client;

    public ReceiptStorageService(S3Properties properties, S3Client client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 사진을 올리고 그 사진이 놓인 자리(객체 키)를 돌려준다.
     *
     * 자리 이름에 정산 번호가 아니라 올린 사람 번호가 들어간다. 사용자가 사진을 먼저 올리고
     * 품목을 확인한 뒤에 정산을 만들기 때문에, 올리는 시점에는 정산이 아직 없다.
     */
    public String upload(long uploaderMemberId, String extension, String contentType, byte[] content) {
        requireExtension(extension);

        String objectKey = KEY_PREFIX + uploaderMemberId + "/" + UUID.randomUUID() + "." + extension;

        client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.getReceiptBucket())
                .key(objectKey)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(content));

        return objectKey;
    }

    public StoredReceipt download(String objectKey) {
        requireReceiptKey(objectKey);

        ResponseBytes<GetObjectResponse> object = client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(properties.getReceiptBucket())
                .key(objectKey)
                .build());

        return new StoredReceipt(object.asByteArray(), object.response().contentType());
    }

    public void delete(String objectKey) {
        requireReceiptKey(objectKey);

        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.getReceiptBucket())
                .key(objectKey)
                .build());
    }

    private static void requireReceiptKey(String objectKey) {
        if (objectKey == null || !objectKey.startsWith(KEY_PREFIX)) {
            throw new IllegalArgumentException("영수증 객체 키는 " + KEY_PREFIX + "로 시작해야 합니다.");
        }
    }

    private static void requireExtension(String extension) {
        if (extension == null || !EXTENSION_PATTERN.matcher(extension).matches()) {
            throw new IllegalArgumentException("영수증 확장자는 소문자와 숫자 1~10자여야 합니다.");
        }
    }
}
