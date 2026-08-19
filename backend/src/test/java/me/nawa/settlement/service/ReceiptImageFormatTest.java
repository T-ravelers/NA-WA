package me.nawa.settlement.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptImageFormatTest {

    @Test
    void detect_jpegSignature_returnsJpeg() {
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

        assertEquals(Optional.of(ReceiptImageFormat.JPEG), ReceiptImageFormat.detect(content));
    }

    @Test
    void detect_pngSignature_returnsPng() {
        byte[] content = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
        };

        assertEquals(Optional.of(ReceiptImageFormat.PNG), ReceiptImageFormat.detect(content));
    }

    @Test
    void detect_webpSignature_returnsWebp() {
        byte[] content = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, content, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, content, 8, 4);

        assertEquals(Optional.of(ReceiptImageFormat.WEBP), ReceiptImageFormat.detect(content));
    }

    @Test
    void detect_textDisguisedAsImage_returnsEmpty() {
        byte[] content = "이건 그냥 글자입니다".getBytes(StandardCharsets.UTF_8);

        assertTrue(ReceiptImageFormat.detect(content).isEmpty());
    }

    @Test
    void detect_tooShortContent_returnsEmpty() {
        assertTrue(ReceiptImageFormat.detect(new byte[]{(byte) 0xFF}).isEmpty());
        assertTrue(ReceiptImageFormat.detect(new byte[0]).isEmpty());
        assertTrue(ReceiptImageFormat.detect(null).isEmpty());
    }

    @Test
    void ofContentType_knownTypeIgnoringCase_returnsFormat() {
        assertEquals(
            Optional.of(ReceiptImageFormat.PNG), ReceiptImageFormat.ofContentType("IMAGE/PNG")
        );
        assertTrue(ReceiptImageFormat.ofContentType("image/gif").isEmpty());
        assertTrue(ReceiptImageFormat.ofContentType(null).isEmpty());
    }
}
