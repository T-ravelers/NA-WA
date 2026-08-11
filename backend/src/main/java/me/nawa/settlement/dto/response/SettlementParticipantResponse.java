package me.nawa.settlement.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 정산 참여자 응답
 *
 * 정산 참여자의 식별 정보와 게임 동의 상태를 반환합니다.
 */
@Getter
@Builder
public class SettlementParticipantResponse {

    private final Long id;
    private final String name;
    private final String initials;
    private final String consentStatus;
}
