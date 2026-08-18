package me.nawa.deposit.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.deposit.mapper.DepositPayoutBatchMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보증금 정산 배치 비동기 처리 스케줄러
 *
 * `PENDING`·`FAILED` 상태의 정산 배치를 60초 주기로 훑어 하나씩 실제 지갑
 * 이체로 처리한다. 각 배치는 {@link DepositPayoutBatchProcessor#processBatch}
 * 안에서 독립된 트랜잭션으로 처리되므로, 하나가 실패해도 나머지 배치 처리에는
 * 영향을 주지 않는다 — 실패한 배치는 `FAILED`로 남아 다음 tick이 다시 집는다.
 */
@Component
@RequiredArgsConstructor
public class DepositPayoutProcessingScheduler {

    private final DepositPayoutBatchMapper depositPayoutBatchMapper;
    private final DepositPayoutBatchProcessor depositPayoutBatchProcessor;

    @Scheduled(fixedDelay = 60_000)
    public void processDuePayoutBatches() {
        List<Long> batchIds = depositPayoutBatchMapper.findPendingOrFailedBatchIds();
        for (Long batchId : batchIds) {
            try {
                depositPayoutBatchProcessor.processBatch(batchId);
            } catch (RuntimeException exception) {
                depositPayoutBatchProcessor.markBatchFailed(batchId);
            }
        }
    }
}
