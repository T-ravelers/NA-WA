package me.nawa.common.storage;

/**
 * 영수증 이미지 데이터 타입
 *
 * S3에서 내려받은 영수증 이미지의 본문과 MIME 타입이다
 */
public record StoredReceipt(
    byte[] content,
    String contentType
) {
}
