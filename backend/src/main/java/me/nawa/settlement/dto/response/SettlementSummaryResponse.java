package me.nawa.settlement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 요약 응답
 *
 * 정산 목록에 표시할 식별자, 제목, 금액, 유형, 상태와 시각을 반환합니다.
 *
 * 시각에 @JsonFormat이 필요한 이유: 이 서비스는 Spring Boot가 아니라서 날짜 형식을
 * 자동으로 맞춰 주지 않습니다. 이 표시가 없으면 LocalDateTime이 문자열이 아니라
 * [2026, 8, 20, 13, 0] 같은 숫자 목록으로 나가고, 받는 쪽에서는 오류도 없이 날짜만
 * 조용히 사라집니다.
 */
@Getter
@Builder
public class SettlementSummaryResponse {

    private final Long id;
    private final String title;
    private final BigDecimal totalAmount;
    private final BigDecimal receivableAmount;
    private final String type;
    private final String status;
    private final SettlementViewerResponse viewer;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime createdAt;

    /** 정산이 끝난 시각. 진행 중이거나 이 값을 남기기 전에 끝난 정산은 비어 있습니다. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime completedAt;
}
