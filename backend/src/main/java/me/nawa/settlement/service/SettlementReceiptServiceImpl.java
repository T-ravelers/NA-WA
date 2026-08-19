package me.nawa.settlement.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.storage.ReceiptStorageService;
import me.nawa.common.storage.StoredReceipt;
import me.nawa.settlement.domain.SettlementReceipt;
import me.nawa.settlement.dto.response.SettlementReceiptUploadResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementReceiptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Service
@RequiredArgsConstructor
public class SettlementReceiptServiceImpl implements SettlementReceiptService {

    private final SettlementReceiptMapper settlementReceiptMapper;
    private final ReceiptStorageService receiptStorageService;

    @Override
    @Transactional
    public SettlementReceiptUploadResponse upload(
        Long memberId,
        String declaredContentType,
        byte[] content
    ) {
        ReceiptImageFormat format = resolveFormat(declaredContentType, content);

        String objectKey = store(memberId, format, content);

        SettlementReceipt receipt = new SettlementReceipt(
            memberId, objectKey, format.getContentType(), content.length
        );
        settlementReceiptMapper.insertReceipt(receipt);

        return SettlementReceiptUploadResponse.builder()
            .receiptId(receipt.getSettlementReceiptId())
            .build();
    }

    @Override
    public void linkToSettlement(Long memberId, Long settlementId, Long receiptId) {
        if (receiptId == null) {
            return;
        }
        int linked = settlementReceiptMapper.linkToSettlement(
            receiptId, settlementId, memberId
        );
        if (linked != 1) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_LINKABLE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoredReceipt getReceipt(Long memberId, Long settlementId) {
        SettlementReceipt receipt = settlementReceiptMapper.findBySettlementIdForViewer(
            settlementId, memberId
        );
        if (receipt == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_FOUND);
        }
        try {
            return receiptStorageService.download(receipt.getObjectKey());
        } catch (NoSuchKeyException exception) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_FOUND);
        } catch (SdkException exception) {
            throw new BusinessException(
                SettlementErrorCode.SETTLEMENT_RECEIPT_STORAGE_UNAVAILABLE
            );
        }
    }

    /**
     * 브라우저가 알려준 형식과 파일 내용에서 읽어낸 형식이 모두 허용 목록에 있고 서로 같을
     * 때만 통과시킨다. 확장자만 이미지로 바꿔 놓은 파일을 걸러내기 위해서다.
     */
    private ReceiptImageFormat resolveFormat(String declaredContentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_FORMAT_INVALID);
        }
        Optional<ReceiptImageFormat> declared =
            ReceiptImageFormat.ofContentType(stripParameters(declaredContentType));
        Optional<ReceiptImageFormat> detected = ReceiptImageFormat.detect(content);
        if (declared.isEmpty() || detected.isEmpty() || declared.get() != detected.get()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_FORMAT_INVALID);
        }
        return detected.get();
    }

    private String store(Long memberId, ReceiptImageFormat format, byte[] content) {
        try {
            return receiptStorageService.upload(
                memberId, format.getExtension(), format.getContentType(), content
            );
        } catch (SdkException exception) {
            throw new BusinessException(
                SettlementErrorCode.SETTLEMENT_RECEIPT_STORAGE_UNAVAILABLE
            );
        }
    }

    /** "image/png; charset=..." 처럼 뒤에 딸린 값을 떼어낸다. */
    private String stripParameters(String contentType) {
        if (contentType == null) {
            return null;
        }
        int separator = contentType.indexOf(';');
        return (separator < 0 ? contentType : contentType.substring(0, separator)).trim();
    }
}
