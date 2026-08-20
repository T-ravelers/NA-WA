package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 영수증에서 읽어낸 품목 한 줄의 초안이다.
 *
 * 값이 비어 있을 수 있다. 못 읽은 자리는 사용자가 화면에서 채운다. 여기 담긴 값은 그대로
 * 저장되지 않으며, 사용자가 확인·수정한 뒤 정산 생성 요청으로 다시 올라온다.
 */
@Getter
@Builder
public class SettlementReceiptOcrItemResponse {
    private final String name;
    private final BigDecimal unitPrice;
    private final BigDecimal quantity;
}
