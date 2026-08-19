package me.nawa.settlement.dto.request;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 정산 생성 요청
 *
 * 원거래, 정산 유형, 참여자와 항목별 입력을 전달합니다.
 *
 * receiptId는 먼저 올려 둔 영수증 사진의 번호입니다. 사진을 붙이지 않으면 비워 둡니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateSettlementRequest {

    private Long sourceTransferId;
    private String type;
    private List<Long> participantAppointmentMemberIds;
    private List<ItemizedSettlementItemRequest> items;
    private Long receiptId;
}
