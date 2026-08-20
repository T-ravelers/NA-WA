package me.nawa.settlement.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 납부 현황 응답
 *
 * 원결제자가 청구한 상대들의 납부 여부와 "몇 명 중 몇 명이 냈는지"를 반환합니다.
 */
@Getter
@Builder
public class SettlementCollectionResponse {

    /**
     * 청구한 사람 수. 원결제자 본인은 세지 않는다.
     *
     * 본인을 세면 아무도 자기 자신에게 돈을 보내지 않으므로 전원이 다 내도 숫자가 끝까지
     * 차지 않는다. 정산이 완료로 넘어가는 시점과 이 숫자가 맞아떨어지게 하려는 것이다.
     */
    private final int totalCount;
    private final int paidCount;
    private final List<SettlementCollectionParticipantResponse> participants;
}
