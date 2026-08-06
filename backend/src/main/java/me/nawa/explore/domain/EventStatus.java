package me.nawa.explore.domain;

/**
 * Event의 운영 상태입니다.
 *
 * 데이터베이스 `event.status` ENUM과 동일한 값을 사용합니다.
 */
public enum EventStatus {

    /** 개최 예정인 Event입니다. */
    SCHEDULED,

    /** 현재 진행 중인 Event입니다. */
    ONGOING,

    /** 종료된 Event입니다. */
    ENDED
}
