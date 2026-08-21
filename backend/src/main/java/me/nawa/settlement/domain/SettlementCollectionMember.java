package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 원결제자가 보는 "돈을 받을 상대" 한 명의 조회 모델이다.
 *
 * 정산에 이름이 올라간 사람 전부가 아니라 실제로 돈을 낼 사람만 담는다. 원결제자 본인도
 * 정산 구성원 행을 가지지만 자기 자신에게 돈을 보내지는 않으므로 여기에는 들어오지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCollectionMember {
    private Long appointmentMemberId;
    private String displayName;
    private BigDecimal shareAmount;
    private String requestStatus;
}
