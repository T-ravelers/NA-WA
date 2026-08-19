package me.nawa.settlement.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

    /**
     * 트랜잭션을 걸지 않는다. 사진을 내려받는 동안(수 MB) DB 커넥션을 붙들고 있을 이유가
     * 없고, 아래에서 만료 표시를 남기는 쓰기가 있어 읽기 전용으로 묶을 수도 없다.
     */
    @Override
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
            // 사진은 보관 기한이 지나면 저장소가 지우는데 지웠다고 알려주지는 않는다.
            // "그런 파일 없다"는 이 응답이 사라졌다는 유일하게 확실한 신호라, 이때 기록을
            // 남긴다. 그래야 다음 조회부터 저장소를 헛되이 부르지 않고, 사진이 언제
            // 사라졌는지도 남는다.
            markExpired(receipt);
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_EXPIRED);
        } catch (SdkException exception) {
            throw new BusinessException(
                SettlementErrorCode.SETTLEMENT_RECEIPT_STORAGE_UNAVAILABLE
            );
        }
    }

    /**
     * 기록에 실패해도 사용자에게는 원래의 만료 응답을 그대로 준다. 사진은 어차피 사라졌고,
     * 기록을 못 남긴 것 때문에 응답까지 500으로 바뀌면 원인이 더 헷갈린다.
     */
    private void markExpired(SettlementReceipt receipt) {
        try {
            settlementReceiptMapper.markExpired(receipt.getSettlementReceiptId());
        } catch (RuntimeException exception) {
            log.warn(
                "영수증 만료 표시 실패, settlementReceiptId={}",
                receipt.getSettlementReceiptId(), exception
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
