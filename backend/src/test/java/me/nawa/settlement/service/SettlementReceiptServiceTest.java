package me.nawa.settlement.service;

import java.nio.charset.StandardCharsets;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.storage.ReceiptStorageService;
import me.nawa.common.storage.StoredReceipt;
import me.nawa.settlement.domain.SettlementReceipt;
import me.nawa.settlement.dto.response.SettlementReceiptUploadResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementReceiptMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementReceiptServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long SETTLEMENT_ID = 69L;
    private static final long RECEIPT_ID = 12L;
    private static final String OBJECT_KEY = "receipts/7/abc.png";

    private static final byte[] PNG_BYTES = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02
    };

    @Mock
    private SettlementReceiptMapper settlementReceiptMapper;

    @Mock
    private ReceiptStorageService receiptStorageService;

    @InjectMocks
    private SettlementReceiptServiceImpl service;

    @Test
    void upload_pngContent_storesObjectAndReturnsReceiptId() {
        when(receiptStorageService.upload(MEMBER_ID, "png", "image/png", PNG_BYTES))
            .thenReturn(OBJECT_KEY);
        doAnswer(invocation -> {
            invocation.getArgument(0, SettlementReceipt.class)
                .setSettlementReceiptId(RECEIPT_ID);
            return null;
        }).when(settlementReceiptMapper).insertReceipt(any(SettlementReceipt.class));

        SettlementReceiptUploadResponse response =
            service.upload(MEMBER_ID, "image/png", PNG_BYTES);

        assertEquals(RECEIPT_ID, response.getReceiptId());
        verify(receiptStorageService).upload(MEMBER_ID, "png", "image/png", PNG_BYTES);
    }

    @Test
    void upload_contentTypeWithCharsetParameter_isAccepted() {
        when(receiptStorageService.upload(MEMBER_ID, "png", "image/png", PNG_BYTES))
            .thenReturn(OBJECT_KEY);

        service.upload(MEMBER_ID, "image/png; charset=binary", PNG_BYTES);

        verify(settlementReceiptMapper).insertReceipt(any(SettlementReceipt.class));
    }

    @Test
    void upload_extensionSaysPngButContentIsText_throwsFormatInvalid() {
        byte[] disguised = "not an image".getBytes(StandardCharsets.UTF_8);

        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.upload(MEMBER_ID, "image/png", disguised)
        );

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_FORMAT_INVALID, exception.getErrorCode()
        );
        verifyNoInteractions(receiptStorageService, settlementReceiptMapper);
    }

    @Test
    void upload_declaredTypeDiffersFromRealFormat_throwsFormatInvalid() {
        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.upload(MEMBER_ID, "image/jpeg", PNG_BYTES)
        );

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_FORMAT_INVALID, exception.getErrorCode()
        );
        verifyNoInteractions(receiptStorageService, settlementReceiptMapper);
    }

    @Test
    void upload_unsupportedContentType_throwsFormatInvalid() {
        assertThrows(
            BusinessException.class, () -> service.upload(MEMBER_ID, "image/gif", PNG_BYTES)
        );
        assertThrows(
            BusinessException.class, () -> service.upload(MEMBER_ID, null, PNG_BYTES)
        );
        assertThrows(
            BusinessException.class, () -> service.upload(MEMBER_ID, "image/png", new byte[0])
        );
    }

    @Test
    void upload_storageFails_throwsStorageUnavailable() {
        when(receiptStorageService.upload(anyLong(), anyString(), anyString(), any()))
            .thenThrow(S3Exception.builder().message("boom").build());

        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.upload(MEMBER_ID, "image/png", PNG_BYTES)
        );

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_STORAGE_UNAVAILABLE, exception.getErrorCode()
        );
        verifyNoInteractions(settlementReceiptMapper);
    }

    @Test
    void linkToSettlement_nullReceiptId_doesNothing() {
        service.linkToSettlement(MEMBER_ID, SETTLEMENT_ID, null);

        verifyNoInteractions(settlementReceiptMapper);
    }

    @Test
    void linkToSettlement_draftOwnedByAnotherMember_throwsNotLinkable() {
        when(settlementReceiptMapper.linkToSettlement(RECEIPT_ID, SETTLEMENT_ID, MEMBER_ID))
            .thenReturn(0);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.linkToSettlement(MEMBER_ID, SETTLEMENT_ID, RECEIPT_ID)
        );

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_LINKABLE, exception.getErrorCode()
        );
    }

    @Test
    void getReceipt_viewerIsNotParticipant_throwsNotFound() {
        when(settlementReceiptMapper.findBySettlementIdForViewer(SETTLEMENT_ID, MEMBER_ID))
            .thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.getReceipt(MEMBER_ID, SETTLEMENT_ID)
        );

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_FOUND, exception.getErrorCode()
        );
        verifyNoInteractions(receiptStorageService);
    }

    @Test
    void getReceipt_participant_returnsStoredBytes() {
        when(settlementReceiptMapper.findBySettlementIdForViewer(SETTLEMENT_ID, MEMBER_ID))
            .thenReturn(receiptRow());
        when(receiptStorageService.download(OBJECT_KEY))
            .thenReturn(new StoredReceipt(PNG_BYTES, "image/png"));

        StoredReceipt receipt = service.getReceipt(MEMBER_ID, SETTLEMENT_ID);

        assertEquals("image/png", receipt.contentType());
        assertEquals(PNG_BYTES.length, receipt.content().length);
    }

    @Test
    void getReceipt_objectMissingInStorage_throwsNotFound() {
        when(settlementReceiptMapper.findBySettlementIdForViewer(SETTLEMENT_ID, MEMBER_ID))
            .thenReturn(receiptRow());
        when(receiptStorageService.download(eq(OBJECT_KEY)))
            .thenThrow(NoSuchKeyException.builder().message("gone").build());

        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.getReceipt(MEMBER_ID, SETTLEMENT_ID)
        );

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_FOUND, exception.getErrorCode()
        );
    }

    private SettlementReceipt receiptRow() {
        SettlementReceipt receipt = new SettlementReceipt(
            MEMBER_ID, OBJECT_KEY, "image/png", PNG_BYTES.length
        );
        receipt.setSettlementReceiptId(RECEIPT_ID);
        receipt.setSettlementId(SETTLEMENT_ID);
        return receipt;
    }
}
