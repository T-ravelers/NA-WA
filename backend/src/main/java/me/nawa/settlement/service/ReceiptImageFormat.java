package me.nawa.settlement.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * 영수증으로 받아들이는 이미지 형식이다.
 *
 * 파일 이름의 확장자나 브라우저가 알려준 형식은 얼마든지 거짓말할 수 있다. 그래서 파일
 * 맨 앞의 몇 바이트(형식마다 정해져 있는 고정된 값)를 직접 읽어 진짜 형식을 판별한다.
 */
public enum ReceiptImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ReceiptImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public static Optional<ReceiptImageFormat> ofContentType(String contentType) {
        return Arrays.stream(values())
            .filter(format -> format.contentType.equalsIgnoreCase(contentType))
            .findFirst();
    }

    /** 파일 내용에서 실제 형식을 읽어낸다. 아는 형식이 아니면 비어 있다. */
    public static Optional<ReceiptImageFormat> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (content.length >= 12
            && startsWith(content, 'R', 'I', 'F', 'F')
            && content[8] == 'W' && content[9] == 'E'
            && content[10] == 'B' && content[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((content[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
