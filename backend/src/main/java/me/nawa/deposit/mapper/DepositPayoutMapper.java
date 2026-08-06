package me.nawa.deposit.mapper;

import java.util.List;

import me.nawa.deposit.domain.AllocationType;
import me.nawa.deposit.domain.DepositPayout;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DepositPayoutMapper {

    /**
     * 보증금 지급 이력 식별자 조회
     * <p>
     * 삭제되지 않은 보증금 지급 이력을 식별자로 조회합니다.
     */
    DepositPayout findById(
        @Param("depositPayoutId") Long depositPayoutId
    );

    /**
     * 이체 기반 보증금 지급 이력 조회
     * <p>
     * 지갑 이체 식별자에 연결된 삭제되지 않은 보증금 지급 이력을 조회합니다.
     */
    DepositPayout findByTransferId(
        @Param("transferId") Long transferId
    );

    /**
     * 정산 배치의 보증금 지급 이력 조회
     * <p>
     * 정산 배치에 포함된 보증금 지급 이력을 식별자 순서로 조회합니다.
     */
    List<DepositPayout> findByBatchId(
        @Param("depositPayoutBatchId") Long depositPayoutBatchId
    );

    /**
     * 원천 보증금의 지급 이력 조회
     * <p>
     * 하나의 원천 보증금에서 발생한 삭제되지 않은 지급 이력을 조회합니다.
     */
    List<DepositPayout> findBySourceDepositId(
        @Param("sourceDepositId") Long sourceDepositId
    );

    /**
     * 보증금 지급 중복 여부 확인
     * <p>
     * 원천 보증금, 수취 회원 및 지급 유형이 일치하는 지급 이력의 개수를 조회합니다.
     */
    int countByAllocation(
        @Param("sourceDepositId") Long sourceDepositId,
        @Param("recipientAppointmentMemberId")
        Long recipientAppointmentMemberId,
        @Param("allocationType")
        AllocationType allocationType
    );

    /**
     * 보증금 지급 이력 저장
     * <p>
     * 보증금 환급 또는 노쇼 보증금 분배 이력을 저장하고,
     * 생성된 지급 이력 식별자를 도메인 객체에 반영합니다.
     */
    int insert(DepositPayout payout);
}
