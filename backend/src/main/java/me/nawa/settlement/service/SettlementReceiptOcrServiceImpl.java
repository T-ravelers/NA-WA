package me.nawa.settlement.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * 올려 둔 영수증 사진을 글자 인식에 보내고, 그 결과를 정산 품목 초안으로 다듬는다.
 *
 * 읽어낸 값은 저장하지 않는다. 사용자가 화면에서 고친 뒤 정산 생성 요청으로 다시 올라오는
 * 값만 남는다. 여기서 저장해 버리면 사진과 품목이 언제 어긋났는지 알 수 없게 된다.
 */
@Service
@RequiredArgsConstructor
public class SettlementReceiptOcrServiceImpl implements SettlementReceiptOcrService {

    /** 정산 품목이 받아들이는 한도다. ItemizedSettlementCreator와 같은 값이어야 한다. */
    private static final int ITEM_NAME_MAX_LENGTH = 200;
    private static final int AMOUNT_SCALE = 4;
    private static final int AMOUNT_INTEGER_DIGITS = 15;
    private static final int QUANTITY_SCALE = 3;
    private static final int QUANTITY_INTEGER_DIGITS = 9;

    private final SettlementReceiptMapper settlementReceiptMapper;
    private final ReceiptStorageService receiptStorageService;
    private final ReceiptOcrClient receiptOcrClient;

    /**
     * 트랜잭션을 걸지 않는다. 사진을 내려받고 바깥 서버의 인식을 기다리는 동안(수 초) DB
     * 커넥션을 붙들고 있을 이유가 없고, 저장하는 것도 없다.
     */
    @Override
    public SettlementReceiptOcrResponse recognize(Long memberId, Long receiptId) {
        SettlementReceipt receipt = settlementReceiptMapper.findDraftForUploader(
            receiptId, memberId
        );
        if (receipt == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_FOUND);
        }

        String imageFormat = ocrFormat(receipt.getContentType());
        StoredReceipt stored = download(receipt.getObjectKey());
        RecognizedReceipt recognized = recognize(stored.content(), imageFormat);

        List<SettlementReceiptOcrItemResponse> items = normalizeItems(recognized.items());
        if (items.isEmpty()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_UNREADABLE);
        }

        return SettlementReceiptOcrResponse.builder()
            .items(items)
            .recognizedTotal(amountOrNull(recognized.totalPrice()))
            .build();
    }

    /**
     * 올릴 때는 통과하지만 글자 인식은 다루지 못하는 형식이 있다. webp가 그렇다.
     *
     * 서버에서 다른 형식으로 바꿔 보내는 길도 있지만, 그러면 사용자가 확인한 사진과 인식에
     * 쓰인 사진이 달라진다. 사진 한 장이 품목의 근거라는 전제가 깨지므로 바꾸지 않고 막는다.
     */
    private String ocrFormat(String contentType) {
        ReceiptImageFormat format = ReceiptImageFormat.ofContentType(contentType)
            .orElseThrow(() -> new BusinessException(
                SettlementErrorCode.SETTLEMENT_RECEIPT_FORMAT_INVALID));

        return switch (format) {
            case JPEG -> "jpg";
            case PNG -> "png";
            case WEBP -> throw new BusinessException(
                SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_FORMAT_UNSUPPORTED);
        };
    }

    /**
     * 초안 사진은 만료 표시를 남기지 않는다. 정산에 붙지 않은 초안은 어떤 화면에도 나오지
     * 않아서 그 표시를 읽을 곳이 없고, 표시해 두면 다음 요청에서 "없는 초안"으로 보여
     * 사라졌다는 사실이 오히려 가려진다.
     */
    private StoredReceipt download(String objectKey) {
        try {
            return receiptStorageService.download(objectKey);
        } catch (NoSuchKeyException exception) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_EXPIRED);
        } catch (SdkException exception) {
            throw new BusinessException(
                SettlementErrorCode.SETTLEMENT_RECEIPT_STORAGE_UNAVAILABLE
            );
        }
    }

    private RecognizedReceipt recognize(byte[] image, String imageFormat) {
        try {
            return receiptOcrClient.recognize(image, imageFormat);
        } catch (ReceiptOcrException exception) {
            throw new BusinessException(switch (exception.getReason()) {
                case UNREADABLE -> SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_UNREADABLE;
                case TIMEOUT -> SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_TIMEOUT;
                case UNAVAILABLE -> SettlementErrorCode.SETTLEMENT_RECEIPT_OCR_UNAVAILABLE;
            }, exception);
        }
    }

    /**
     * 이름도 금액도 못 읽은 줄은 화면에 빈 칸만 하나 늘릴 뿐이라 버린다. 둘 중 하나라도
     * 읽혔으면 남긴다. 사용자가 나머지 한 칸만 채우는 편이 처음부터 입력하는 것보다 빠르다.
     */
    private List<SettlementReceiptOcrItemResponse> normalizeItems(
        List<RecognizedReceiptItem> recognizedItems
    ) {
        List<SettlementReceiptOcrItemResponse> items = new ArrayList<>();
        for (RecognizedReceiptItem recognized : recognizedItems) {
            String name = name(recognized.name());
            Priced priced = price(recognized);
            if (name == null && priced.unitPrice() == null) {
                continue;
            }
            items.add(SettlementReceiptOcrItemResponse.builder()
                .name(name)
                .unitPrice(priced.unitPrice())
                .quantity(priced.quantity())
                .build());
        }
        return List.copyOf(items);
    }

    /**
     * 줄 합계와 수량으로 낱개 값을 정한다.
     *
     * 줄 합계를 먼저 믿는 이유는, 정산은 품목 합계가 원결제 금액과 **정확히** 맞아야
     * 만들어지기 때문이다. 낱개 값 쪽을 믿으면 나누어떨어지지 않는 영수증에서 몇 원씩
     * 어긋나 정산이 아예 거절된다.
     *
     * 나누어떨어지지 않으면 수량을 1로 두고 줄 합계를 통째로 낱개 값에 넣는다. 수량 정보는
     * 잃지만 금액은 어긋나지 않는다. 수량은 사용자가 다시 넣을 수 있어도 금액은 그렇지 않다.
     */
    private Priced price(RecognizedReceiptItem recognized) {
        BigDecimal quantity = quantityOrOne(recognized.quantity());
        BigDecimal lineTotal = amountOrNull(recognized.lineTotal());

        if (lineTotal == null) {
            return new Priced(amountOrNull(recognized.unitPrice()), quantity);
        }
        if (quantity.compareTo(BigDecimal.ONE) == 0) {
            return new Priced(lineTotal, BigDecimal.ONE);
        }
        try {
            BigDecimal unitPrice = amountOrNull(lineTotal.divide(quantity));
            if (unitPrice != null) {
                return new Priced(unitPrice, quantity);
            }
        } catch (ArithmeticException exception) {
            // 나누어떨어지지 않는다. 아래에서 한 줄을 통째로 한 개로 본다.
        }
        return new Priced(lineTotal, BigDecimal.ONE);
    }

    private String name(String recognized) {
        if (recognized == null || recognized.isBlank()) {
            return null;
        }
        String trimmed = recognized.trim();
        return trimmed.length() <= ITEM_NAME_MAX_LENGTH
            ? trimmed
            : trimmed.substring(0, ITEM_NAME_MAX_LENGTH);
    }

    /** 수량이 없거나 정산이 받아들이지 못할 값이면 한 개로 본다. 영수증 대부분이 한 개다. */
    private BigDecimal quantityOrOne(BigDecimal recognized) {
        return Optional.ofNullable(recognized)
            .map(this::plain)
            .filter(quantity -> quantity.signum() > 0)
            .filter(quantity -> withinRange(quantity, QUANTITY_SCALE, QUANTITY_INTEGER_DIGITS))
            .orElse(BigDecimal.ONE);
    }

    /** 정산이 받아들이지 못할 금액은 비워 둔다. 잘못 읽은 값을 화면까지 나르지 않는다. */
    private BigDecimal amountOrNull(BigDecimal recognized) {
        return Optional.ofNullable(recognized)
            .map(this::plain)
            .filter(amount -> amount.signum() >= 0)
            .filter(amount -> withinRange(amount, AMOUNT_SCALE, AMOUNT_INTEGER_DIGITS))
            .orElse(null);
    }

    /**
     * 꼬리의 0을 떼되 지수 표기로 넘어가지 않게 한다.
     *
     * BigDecimal에서 1000의 0을 떼면 1E+3이 되고, 그대로 JSON에 실으면 응답에 "1E+3"이
     * 찍힌다. 사람이 읽는 금액 화면에 그 표기가 나가면 안 된다.
     */
    private BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private boolean withinRange(BigDecimal value, int maxScale, int maxIntegerDigits) {
        return value.scale() <= maxScale
            && value.precision() - value.scale() <= maxIntegerDigits;
    }

    private record Priced(BigDecimal unitPrice, BigDecimal quantity) {
    }
}
