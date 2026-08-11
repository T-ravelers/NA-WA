package me.nawa.settlement.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 정산 생성 응답
 *
 * 새로 생성한 정산의 식별자를 반환합니다.
 */
@Getter
@Builder
public class SettlementCreateResponse {

    private final Long id;
}
