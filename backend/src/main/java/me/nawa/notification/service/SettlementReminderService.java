package me.nawa.notification.service;

import java.time.LocalDateTime;

/**
 * 오래 내지 않은 정산 구성원에게 리마인더 알림을 보내는 실행 한 번의 계약이다.
 */
public interface SettlementReminderService {

    /**
     * 기준 시각 {@code now}에 보낼 사람을 묶음 단위로 끝까지 골라 리마인더를 적고 푸시를 요청한다.
     *
     * 몇 번을 불러도, 동시에 불러도, 중간에 멈췄다 다시 불러도 한 사람에게 같은 날 두 번 가지
     * 않는다. 처리한 사람은 발송 기록 때문에 다음 호출의 대상에서 빠지므로, 멈춘 실행은 다시
     * 부르기만 하면 남은 사람부터 이어진다.
     *
     * 시각은 {@code now} 하나만 쓴다. 호출한 쪽이 넘겨야 하는 이유는 DB 시계에 기대지 않기
     * 위해서이고, 테스트가 임의의 날짜로 실행해 볼 수 있게 하기 위해서이기도 하다.
     */
    SettlementReminderResult sendReminders(LocalDateTime now);
}
