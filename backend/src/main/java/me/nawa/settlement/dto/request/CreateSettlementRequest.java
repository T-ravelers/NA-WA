package me.nawa.settlement.dto.request;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 정산 생성 요청
 *
 * 원거래, 정산 유형, 참여자와 항목별 입력을 전달합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateSettlementRequest {

    private Long sourceTransferId;
    private String type;
    private List<Long> participantAppointmentMemberIds;
    private List<ItemizedSettlementItemRequest> items;
}
