package me.nawa.common.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3ConfigTest {

    private final S3Config config = new S3Config();

    private static S3Properties properties(String accessKeyId, String secretAccessKey) {
        return new S3Properties("ap-northeast-2", "nawa-receipts-test", accessKeyId, secretAccessKey);
    }

    @Test
    void s3Client_blankCredentials_createsClient() {
        assertDoesNotThrow(() -> config.s3Client(properties("", "")).close());
    }

    @Test
    void s3Client_bothCredentialsPresent_createsClient() {
        assertDoesNotThrow(() -> config.s3Client(properties("AKIAEXAMPLE", "secret")).close());
    }

    @Test
    void s3Client_accessKeyIdOnly_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> config.s3Client(properties("AKIAEXAMPLE", "")));
    }

    @Test
    void s3Client_secretAccessKeyOnly_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> config.s3Client(properties("", "secret")));
    }
}
