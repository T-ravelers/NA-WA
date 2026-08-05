package me.nawa.deposit.mapper;

import java.time.LocalDateTime;

import me.nawa.deposit.domain.Deposit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DepositMapper {

    /**
     * 보증금 식별자 조회
     *
     * 삭제되지 않은 보증금을 식별자로 조회합니다.
     */
    Deposit findById(
        @Param("depositId") Long depositId
    );

    /**
     * 상태 변경을 위한 보증금 잠금 조회
     *
     * 보증금 상태 변경 전에 해당 행을 조회하고,
     * 트랜잭션이 종료될 때까지 다른 트랜잭션의 변경을 제한합니다.
     */
    Deposit findByIdForUpdate(
        @Param("depositId") Long depositId
    );

    /**
     * 약속 참여 회원의 보증금 조회
     *
     * 삭제되지 않은 약속 참여 회원의 보증금 정보를 조회합니다.
     */
    Deposit findByAppointmentMemberId(
        @Param("appointmentMemberId") Long appointmentMemberId
    );

    /**
     * 보증금 납부 대기 행 저장
     *
     * 약속 참여 회원의 보증금을 `PENDING` 상태로 저장하고,
     * 생성된 보증금 식별자를 도메인 객체에 반영합니다.
     */
    int insert(Deposit deposit);

    /**
     * 보증금 예치 완료 반영
     *
     * `PENDING` 상태의 보증금을 회원 지갑에서 `SYSTEM_ESCROW`로
     * 이체한 후 `HELD` 상태로 변경합니다.
     */
    int markHeld(
        @Param("depositId") Long depositId,
        @Param("transferId") Long transferId,
        @Param("heldAt") LocalDateTime heldAt
    );

    /**
     * 보증금 납부 전 취소 반영
     *
     * `PENDING` 상태의 보증금을 보증금 납부 전 약속 취소에 따라
     * `CANCELLED` 상태로 변경합니다.
     */
    int markCancelled(
        @Param("depositId") Long depositId,
        @Param("resolvedAt") LocalDateTime resolvedAt
    );

    /**
     * 보증금 환급 완료 반영
     *
     * `HELD` 상태의 보증금을 참석 회원에게 환급한 후
     * `REFUNDED` 상태로 변경합니다.
     */
    int markRefunded(
        @Param("depositId") Long depositId,
        @Param("resolvedAt") LocalDateTime resolvedAt
    );

    /**
     * 노쇼 보증금 분배 완료 반영
     *
     * `HELD` 상태의 보증금을 참석 회원에게 분배한 후
     * `DISTRIBUTED` 상태로 변경합니다.
     */
    int markDistributed(
        @Param("depositId") Long depositId,
        @Param("resolvedAt") LocalDateTime resolvedAt
    );
}
