package me.nawa.deposit.mapper;

import java.util.List;
import me.nawa.deposit.domain.DepositPayoutBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DepositPayoutBatchMapper {

    /**
     * 처리 대기 정산 배치 식별자 조회
     * <p>
     * `PENDING` 또는 `FAILED` 상태의 정산 배치 식별자를 오름차순으로 조회한다.
     * 비동기 처리 스케줄러가 매 tick마다 이 목록을 가져가 순차 처리한다.
     */
    List<Long> findPendingOrFailedBatchIds();

    /**
     * 정산 배치 식별자 조회
     * <p>
     * 삭제되지 않은 정산 배치를 식별자로 조회합니다.
     */
    DepositPayoutBatch findById(
        @Param("depositPayoutBatchId") Long depositPayoutBatchId
    );

    /**
     * 상태 변경을 위한 정산 배치 잠금 조회
     * <p>
     * 정산 배치 상태 변경 전에 해당 행을 조회하고,
     * 트랜잭션이 종료될 때까지 다른 트랜잭션의 변경을 제한합니다.
     */
    DepositPayoutBatch findByIdForUpdate(
        @Param("depositPayoutBatchId") Long depositPayoutBatchId
    );

    /**
     * 약속별 정산 배치 조회
     * <p>
     * 약속에 연결된 삭제되지 않은 정산 배치를 조회합니다.
     */
    DepositPayoutBatch findByAppointmentId(
        @Param("appointmentId") Long appointmentId
    );

    /**
     * 멱등성 키를 이용한 정산 배치 조회
     * <p>
     * 정산 중복 처리를 방지하기 위해 멱등성 키가 일치하는 배치를 조회합니다.
     */
    DepositPayoutBatch findByIdempotencyKey(
        @Param("idempotencyKey") String idempotencyKey
    );

    /**
     * 정산 대기 배치 저장
     * <p>
     * 약속의 보증금 정산 배치를 `PENDING` 상태로 저장하고,
     * 생성된 정산 배치 식별자를 도메인 객체에 반영합니다.
     */
    int insert(DepositPayoutBatch batch);

    /**
     * 정산 처리 시작
     * <p>
     * `PENDING` 또는 `FAILED` 상태의 정산 배치를
     * `PROCESSING` 상태로 변경합니다.
     */
    int markProcessing(
        @Param("depositPayoutBatchId") Long depositPayoutBatchId
    );

    /**
     * 정산 완료 반영
     * <p>
     * `PROCESSING` 상태의 정산 배치에 정산 결과를 기록하고
     * `COMPLETED` 상태로 변경합니다.
     */
    int markCompleted(DepositPayoutBatch batch);

    /**
     * 정산 실패 반영
     * <p>
     * `PROCESSING` 상태의 정산 배치를 오류 발생에 따라
     * `FAILED` 상태로 변경합니다.
     */
    int markFailed(
        @Param("depositPayoutBatchId") Long depositPayoutBatchId
    );
}
