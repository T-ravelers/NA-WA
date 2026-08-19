package me.nawa.common.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptStorageServiceTest {

    private static final String BUCKET = "nawa-receipts-test";

    private static final long UPLOADER_MEMBER_ID = 42L;

    @Mock
    private S3Client client;

    private ReceiptStorageService service() {
        return new ReceiptStorageService(
            new S3Properties("ap-northeast-2", BUCKET, "", ""), client);
    }

    @Test
    void upload_allowedExtension_putsObjectWithUploaderScopedKey() {
        byte[] content = "receipt".getBytes(StandardCharsets.UTF_8);

        String objectKey = service().upload(UPLOADER_MEMBER_ID, "png", "image/png", content);

        assertTrue(objectKey.matches("receipts/42/[0-9a-f-]{36}\\.png"), objectKey);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals(objectKey, request.getValue().key());
        assertEquals("image/png", request.getValue().contentType());
    }

    @Test
    void upload_extensionOutsideAllowedPattern_throwsIllegalArgument() {
        ReceiptStorageService service = service();
        byte[] content = "receipt".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> service.upload(UPLOADER_MEMBER_ID, "../png", "image/png", content));
        assertThrows(IllegalArgumentException.class, () -> service.upload(UPLOADER_MEMBER_ID, "p/ng", "image/png", content));
        assertThrows(IllegalArgumentException.class, () -> service.upload(UPLOADER_MEMBER_ID, "PNG", "image/png", content));
        assertThrows(IllegalArgumentException.class, () -> service.upload(UPLOADER_MEMBER_ID, "", "image/png", content));
        assertThrows(IllegalArgumentException.class, () -> service.upload(UPLOADER_MEMBER_ID, null, "image/png", content));

        verifyNoInteractions(client);
    }

    @Test
    void download_storedKey_returnsContentAndContentType() {
        byte[] content = "receipt".getBytes(StandardCharsets.UTF_8);
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
            ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentType("image/png").build(), content));

        StoredReceipt receipt = service().download("receipts/42/probe.png");

        assertArrayEquals(content, receipt.content());
        assertEquals("image/png", receipt.contentType());

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObjectAsBytes(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals("receipts/42/probe.png", request.getValue().key());
    }

    @Test
    void delete_storedKey_removesObjectFromBucket() {
        service().delete("receipts/42/probe.png");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals("receipts/42/probe.png", request.getValue().key());
    }

    @Test
    void download_keyOutsideReceiptsPrefix_throwsIllegalArgument() {
        ReceiptStorageService service = service();

        assertThrows(IllegalArgumentException.class, () -> service.download("elsewhere/probe.png"));
        assertThrows(IllegalArgumentException.class, () -> service.download("/receipts/probe.png"));
        assertThrows(IllegalArgumentException.class, () -> service.download(null));

        verify(client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void delete_keyOutsideReceiptsPrefix_throwsIllegalArgument() {
        ReceiptStorageService service = service();

        assertThrows(IllegalArgumentException.class, () -> service.delete("elsewhere/probe.png"));
        assertThrows(IllegalArgumentException.class, () -> service.delete(null));

        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
