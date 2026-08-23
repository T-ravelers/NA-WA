package me.nawa.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 알림 한 건 응답
 *
 * 시각에 @JsonFormat이 필요한 이유: 이 서비스는 Spring Boot가 아니라서 날짜 형식을
 * 자동으로 맞춰 주지 않습니다. 이 표시가 없으면 LocalDateTime이 문자열이 아니라
 * [2026, 8, 21, 13, 0] 같은 숫자 목록으로 나가고, 받는 쪽에서는 오류도 없이 날짜만
 * 조용히 사라집니다.
 */
@Getter
@Builder
public class NotificationResponse {

    private final Long id;
    private final String type;
    private final Long settlementId;
    private final String actorName;
    private final String gatheringName;
    private final BigDecimal amount;
    private final String currencyCode;

    /** 읽은 시각. 아직 안 읽었으면 비어 있습니다. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime readAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime createdAt;
}
