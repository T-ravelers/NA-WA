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
    void 자격증명이_비어도_클라이언트가_생성된다() {
        assertDoesNotThrow(() -> config.s3Client(properties("", "")).close());
    }

    @Test
    void 자격증명이_있으면_클라이언트가_생성된다() {
        assertDoesNotThrow(() -> config.s3Client(properties("AKIAEXAMPLE", "secret")).close());
    }

    @Test
    void 액세스키만_설정되면_기동을_막는다() {
        assertThrows(IllegalStateException.class, () -> config.s3Client(properties("AKIAEXAMPLE", "")));
    }

    @Test
    void 시크릿키만_설정되면_기동을_막는다() {
        assertThrows(IllegalStateException.class, () -> config.s3Client(properties("", "secret")));
    }
}
