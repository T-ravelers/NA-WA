package me.nawa.settlement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 후보 응답
 *
 * 정산 생성에 사용할 원거래와 여정·약속·결제자 및 참여자 정보를 반환합니다.
 */
@Getter
@Builder
public class SettlementCandidateResponse {

    private final Long transferId;
    private final String journeyName;
    private final String gatheringName;
    private final String merchantName;
    private final BigDecimal amount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final LocalDateTime paidAt;
    private final String payerName;
    private final List<SettlementParticipantResponse> participants;
}
