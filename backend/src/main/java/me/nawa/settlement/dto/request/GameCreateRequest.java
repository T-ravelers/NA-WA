package me.nawa.settlement.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 게임 정산 생성 정보
 *
 * 생성할 게임의 유형과 최종 부담자 수를 정산 생성 요청에 포함합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class GameCreateRequest {

    private String type;
    private Integer liableCount;
}
