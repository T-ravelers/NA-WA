package me.nawa.notification.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정산 구성원 한 명의 회원 번호·이름·부담금·지급 여부. 누구에게 얼마로 보낼지를 이걸로 정한다. */
@Getter
@Setter
@NoArgsConstructor
public class SettlementMemberShare {
    private Long memberId;
    private String displayName;
    private BigDecimal shareAmount;
    private String requestStatus;
}
