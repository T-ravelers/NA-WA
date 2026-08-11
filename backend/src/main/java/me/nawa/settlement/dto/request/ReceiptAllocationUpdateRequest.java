package me.nawa.settlement.dto.request;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 영수증 배분 수정 요청
 *
 * 영수증 분석에 저장할 항목별 참여자 배분 목록을 전달합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReceiptAllocationUpdateRequest {

    private List<ReceiptAllocationRequest> allocations;
}
