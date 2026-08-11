package me.nawa.settlement.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 게임 동의 요청
 *
 * 게임형 정산에 대한 참여자의 동의 또는 거절 상태를 전달합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class GameConsentRequest {

    private String status;
}
