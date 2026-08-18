package me.nawa.common.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceiptStorageServiceTest {

    private static S3Properties properties(String accessKeyId, String secretAccessKey) {
        return new S3Properties("ap-northeast-2", "nawa-receipts-test", accessKeyId, secretAccessKey);
    }

    /*
     * 로컬 개발자는 S3 키 없이도 서버를 띄운다. 키가 비었을 때 AwsBasicCredentials가
     * 예외를 던지면 컨텍스트가 통째로 안 뜨므로, 기본 자격증명 체인으로 넘어가는지 확인한다.
     */
    @Test
    void 자격증명이_비어도_서비스가_생성된다() {
        assertDoesNotThrow(() -> new ReceiptStorageService(properties("", "")));
    }

    @Test
    void 자격증명이_있으면_서비스가_생성된다() {
        assertDoesNotThrow(() -> new ReceiptStorageService(properties("AKIAEXAMPLE", "secret")));
    }

    /*
     * IAM 정책이 receipts/ 접두사로 좁혀져 있다. DB에 잘못된 키가 들어와도
     * S3 호출까지 나가지 않고 여기서 막혀야 한다.
     */
    @Test
    void 접두사를_벗어난_키는_조회를_거부한다() {
        ReceiptStorageService service = new ReceiptStorageService(properties("", ""));

        assertThrows(IllegalArgumentException.class, () -> service.download("elsewhere/probe.png"));
        assertThrows(IllegalArgumentException.class, () -> service.download("/receipts/probe.png"));
        assertThrows(IllegalArgumentException.class, () -> service.download(null));
    }

    @Test
    void 접두사를_벗어난_키는_삭제를_거부한다() {
        ReceiptStorageService service = new ReceiptStorageService(properties("", ""));

        assertThrows(IllegalArgumentException.class, () -> service.delete("elsewhere/probe.png"));
        assertThrows(IllegalArgumentException.class, () -> service.delete(null));
    }
}
