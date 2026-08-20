package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 영수증 글자 인식 결과다.
 *
 * recognizedTotal은 영수증에 찍힌 합계 금액이다. 품목을 다 더한 값과 다를 수 있어서
 * 사용자가 견주어 볼 기준으로만 쓰고, 정산 금액으로 그대로 쓰지 않는다.
 */
@Getter
@Builder
public class SettlementReceiptOcrResponse {
    private final List<SettlementReceiptOcrItemResponse> items;
    private final BigDecimal recognizedTotal;
}
