package me.nawa.settlement.dto.request;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 영수증 항목 수정 요청
 *
 * 영수증 분석 결과에 반영할 수정된 항목 목록을 전달합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReceiptItemUpdateRequest {

    private List<ReceiptItemRequest> items;
}
