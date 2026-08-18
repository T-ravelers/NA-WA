package me.nawa.common.storage;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 정적 자격증명 설정
 *
 * 영수증 버킷에 접근할 리전, 버킷명, 접근키, 시크릿키 설정값이다
 */
@Getter
@Component
public class S3Properties {

    private final String region;
    private final String receiptBucket;
    private final String accessKeyId;
    private final String secretAccessKey;

    public S3Properties(
        @Value("${aws.region}") String region,
        @Value("${aws.s3.receipt-bucket}") String receiptBucket,
        @Value("${aws.access-key-id}") String accessKeyId,
        @Value("${aws.secret-access-key}") String secretAccessKey
    ) {
        this.region = region;
        this.receiptBucket = receiptBucket;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }
}
