package me.nawa.settlement.service;

import java.math.BigDecimal;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.ocr.ReceiptOcrClient;
import me.nawa.common.ocr.ReceiptOcrException;
import me.nawa.common.ocr.RecognizedReceipt;
import me.nawa.common.ocr.RecognizedReceiptItem;
import me.nawa.common.storage.ReceiptStorageService;
import me.nawa.common.storage.StoredReceipt;
import me.nawa.settlement.domain.SettlementReceipt;
import me.nawa.settlement.dto.response.SettlementReceiptOcrItemResponse;
import me.nawa.settlement.dto.response.SettlementReceiptOcrResponse;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementReceiptOcrServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long RECEIPT_ID = 12L;
    private static final String OBJECT_KEY = "receipts/7/abc.jpg";
    private static final byte[] IMAGE = {1, 2, 3};

    @Mock
    private SettlementReceiptMapper settlementReceiptMapper;

    @Mock
    private ReceiptStorageService receiptStorageService;

    @Mock
    private ReceiptOcrClient receiptOcrClient;

    @InjectMocks
    private SettlementReceiptOcrServiceImpl service;

    @Test
    void recognize_draftReceipt_returnsItemDraft() {
        givenDraft("image/jpeg");
        givenStoredImage();
        givenRecognized(
            new RecognizedReceiptItem("아메리카노", bd("2"), bd("4500"), bd("9000")),
            bd("9000")
        );

        SettlementReceiptOcrResponse response = service.recognize(MEMBER_ID, RECEIPT_ID);

        SettlementReceiptOcrItemResponse item = response.getItems().get(0);
        assertEquals("아메리카노", item.getName());
        assertEquals(bd("4500"), item.getUnitPrice());
        assertEquals(bd("2"), item.getQuantity());
        assertEquals(bd("9000"), response.getRecognizedTotal());
    }

    /** 남의 초안이거나 이미 정산에 붙은 사진이면 조회 자체가 비어 오고, 저장소를 부르지 않는다. */
    @Test
    void recognize_notOwnDraft_throwsNotFound() {
        when(settlementReceiptMapper.findDraftForUploader(RECEIPT_ID, MEMBER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.recognize(MEMBER_ID, RECEIPT_ID)
        );

        assertEquals(SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(receiptStorageService, receiptOcrClient);
    }

    /** webp는 올릴 수는 있지만 글자 인식이 다루지 못한다. 저장소 오류와 구분해 알려준다. */
    @Test
    void recognize_webpReceipt_throwsFormatUnsupported() {
        givenDraft("image/webp");

        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.recognize(MEMBER_ID, RECEIPT_ID)
        );

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_FORMAT_UNSUPPORTED,
            exception.getErrorCode()
        );
        verifyNoInteractions(receiptStorageService, receiptOcrClient);
    }

    @Test
    void recognize_photoAlreadyGone_throwsExpired() {
        givenDraft("image/jpeg");
        when(receiptStorageService.download(OBJECT_KEY))
            .thenThrow(NoSuchKeyException.builder().message("gone").build());

        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.recognize(MEMBER_ID, RECEIPT_ID)
        );

        assertEquals(SettlementErrorCode.SETTLEMENT_RECEIPT_EXPIRED, exception.getErrorCode());
    }

    @Test
    void recognize_storageDown_throwsStorageUnavailable() {
        givenDraft("image/jpeg");
        when(receiptStorageService.download(OBJECT_KEY))
            .thenThrow(S3Exception.builder().message("down").build());

        BusinessException exception = assertThrows(
            BusinessException.class, () -> service.recognize(MEMBER_ID, RECEIPT_ID)
        );

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_STORAGE_UNAVAILABLE, exception.getErrorCode()
        );
    }

    /** 인식 실패의 세 갈래는 화면이 다르게 안내해야 하므로 오류 코드도 갈라져야 한다. */
    @Test
    void recognize_ocrTimeout_throwsTimeoutCode() {
        givenOcrFailure(ReceiptOcrException.Reason.TIMEOUT);

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_TIMEOUT,
            assertThrows(
                BusinessException.class, () -> service.recognize(MEMBER_ID, RECEIPT_ID)
            ).getErrorCode()
        );
    }

    @Test
    void recognize_ocrUnavailable_throwsUnavailableCode() {
        givenOcrFailure(ReceiptOcrException.Reason.UNAVAILABLE);

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_UNAVAILABLE,
            assertThrows(
                BusinessException.class, () -> service.recognize(MEMBER_ID, RECEIPT_ID)
            ).getErrorCode()
        );
    }

    @Test
    void recognize_ocrCannotRead_throwsUnreadableCode() {
        givenOcrFailure(ReceiptOcrException.Reason.UNREADABLE);

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_UNREADABLE,
            assertThrows(
                BusinessException.class, () -> service.recognize(MEMBER_ID, RECEIPT_ID)
            ).getErrorCode()
        );
    }

    /** 이름도 금액도 못 읽은 줄만 남으면 사용자에게 보여줄 것이 없다. */
    @Test
    void recognize_noUsableItem_throwsUnreadableCode() {
        givenDraft("image/jpeg");
        givenStoredImage();
        givenRecognized(new RecognizedReceiptItem(" ", null, null, null), null);

        assertEquals(
            SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_UNREADABLE,
            assertThrows(
                BusinessException.class, () -> service.recognize(MEMBER_ID, RECEIPT_ID)
            ).getErrorCode()
        );
    }

    /**
     * 줄 합계가 수량으로 나누어떨어지지 않으면 수량을 1로 낮추고 합계를 통째로 낱개 값에
     * 넣는다. 품목 합계가 원결제 금액과 정확히 맞아야 정산이 만들어지기 때문이다.
     */
    @Test
    void recognize_lineTotalNotDivisible_keepsTotalAndDropsQuantity() {
        givenDraft("image/jpeg");
        givenStoredImage();
        givenRecognized(new RecognizedReceiptItem("삼겹살", bd("3"), null, bd("10000")), null);

        SettlementReceiptOcrItemResponse item =
            service.recognize(MEMBER_ID, RECEIPT_ID).getItems().get(0);

        assertEquals(bd("10000"), item.getUnitPrice());
        assertEquals(BigDecimal.ONE, item.getQuantity());
    }

    /** 수량을 못 읽으면 한 개로 본다. 영수증 대부분이 한 개다. */
    @Test
    void recognize_missingQuantity_treatsAsOne() {
        givenDraft("image/jpeg");
        givenStoredImage();
        givenRecognized(new RecognizedReceiptItem("치즈케이크", null, null, bd("7500")), null);

        SettlementReceiptOcrItemResponse item =
            service.recognize(MEMBER_ID, RECEIPT_ID).getItems().get(0);

        assertEquals(bd("7500"), item.getUnitPrice());
        assertEquals(BigDecimal.ONE, item.getQuantity());
    }

    /** 금액을 못 읽은 줄도 이름이 있으면 남긴다. 빈 칸만 채우는 편이 다시 입력하는 것보다 낫다. */
    @Test
    void recognize_nameWithoutPrice_keepsItemWithEmptyPrice() {
        givenDraft("image/jpeg");
        givenStoredImage();
        givenRecognized(new RecognizedReceiptItem("콜라", null, null, null), null);

        SettlementReceiptOcrItemResponse item =
            service.recognize(MEMBER_ID, RECEIPT_ID).getItems().get(0);

        assertEquals("콜라", item.getName());
        assertNull(item.getUnitPrice());
    }

    private void givenDraft(String contentType) {
        SettlementReceipt receipt = new SettlementReceipt(
            MEMBER_ID, OBJECT_KEY, contentType, IMAGE.length
        );
        receipt.setSettlementReceiptId(RECEIPT_ID);
        when(settlementReceiptMapper.findDraftForUploader(RECEIPT_ID, MEMBER_ID))
            .thenReturn(receipt);
    }

    private void givenStoredImage() {
        when(receiptStorageService.download(OBJECT_KEY))
            .thenReturn(new StoredReceipt(IMAGE, "image/jpeg"));
    }

    private void givenRecognized(RecognizedReceiptItem item, BigDecimal totalPrice) {
        when(receiptOcrClient.recognize(any(), anyString()))
            .thenReturn(new RecognizedReceipt(List.of(item), totalPrice));
    }

    private void givenOcrFailure(ReceiptOcrException.Reason reason) {
        givenDraft("image/jpeg");
        givenStoredImage();
        when(receiptOcrClient.recognize(any(), anyString()))
            .thenThrow(new ReceiptOcrException(reason, "실패"));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
