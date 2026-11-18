package me.nawa.appointment.event;

/**
 * 참여자 한 명이 약속에서 나갔다(#612).
 *
 * 참여 취소 트랜잭션 안에서 발행하고, 그 트랜잭션이 커밋된 뒤에만 Redis 입장권 자리를 돌려준다.
 * 여정에서 약속 항목을 지우는 경로처럼 바깥 트랜잭션 안에서 나간 경우에는 바깥이 커밋될 때 처리된다.
 */
public record AppointmentMemberLeftEvent(Long appointmentId, Long memberId) {
}
