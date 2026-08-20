package me.nawa.common.ocr;

import java.math.BigDecimal;
import java.util.List;

/**
 * 영수증 사진 한 장에서 읽어낸 결과다.
 *
 * totalPrice는 영수증에 찍힌 합계 금액이다. 품목을 다 더한 값과 다를 수 있는데, 할인이나
 * 봉사료처럼 품목 줄에 없는 금액이 붙기 때문이다. 그래서 계산해서 채우지 않고 읽은 그대로
 * 둔다. 사용자가 품목 합계와 견주어 볼 기준값으로만 쓴다.
 */
public record RecognizedReceipt(
    List<RecognizedReceiptItem> items,
    BigDecimal totalPrice
) {
}
