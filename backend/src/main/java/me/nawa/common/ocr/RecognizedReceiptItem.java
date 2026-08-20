package me.nawa.common.ocr;

import java.math.BigDecimal;

/**
 * 영수증 사진에서 읽어낸 품목 한 줄이다.
 *
 * 모든 값이 비어 있을 수 있다. 영수증이 접혔거나 흐리면 글자 인식은 이름만 읽고 금액은
 * 못 읽기도 한다. 그런 줄을 버리지 않고 그대로 올려 보내야 사용자가 화면에서 빠진 값만
 * 채워 넣을 수 있다.
 *
 * lineTotal은 그 줄에 찍힌 합계 금액이고 unitPrice는 낱개 값이다. 영수증마다 둘 중 하나만
 * 있는 경우가 흔해서 둘을 따로 들고 있는다.
 */
public record RecognizedReceiptItem(
    String name,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {
}
