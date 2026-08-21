package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 납부 현황의 참여자 한 명 응답
 *
 * 원결제자가 청구한 상대 한 명의 이름과 부담금, 납부 여부를 반환합니다.
 */
@Getter
@Builder
public class SettlementCollectionParticipantResponse {

    /** 회원 번호가 아니라 약속 참가 행 번호(appointment_member_id)다. 정산은 이 값으로 사람을 가린다. */
    private final Long id;
    private final String name;
    private final String initials;
    private final BigDecimal shareAmount;
    private final String requestStatus;
}
