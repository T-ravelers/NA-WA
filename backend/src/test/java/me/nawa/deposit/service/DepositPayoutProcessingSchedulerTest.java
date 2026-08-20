package me.nawa.deposit.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import me.nawa.deposit.mapper.DepositPayoutBatchMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepositPayoutProcessingSchedulerTest {

    @Mock
    private DepositPayoutBatchMapper depositPayoutBatchMapper;
    @Mock
    private DepositPayoutBatchProcessor depositPayoutBatchProcessor;
    @InjectMocks
    private DepositPayoutProcessingScheduler scheduler;

    @Test
    void processDuePayoutBatches_processesEveryPendingOrFailedBatch() {
        when(depositPayoutBatchMapper.findPendingOrFailedBatchIds())
                .thenReturn(List.of(1L, 2L, 3L));

        scheduler.processDuePayoutBatches();

        verify(depositPayoutBatchProcessor).processBatch(1L);
        verify(depositPayoutBatchProcessor).processBatch(2L);
        verify(depositPayoutBatchProcessor).processBatch(3L);
    }

    @Test
    void processDuePayoutBatches_onFailure_marksFailedAndContinuesWithNextBatch() {
        when(depositPayoutBatchMapper.findPendingOrFailedBatchIds())
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("transient failure"))
                .when(depositPayoutBatchProcessor).processBatch(2L);

        scheduler.processDuePayoutBatches();

        verify(depositPayoutBatchProcessor).processBatch(1L);
        verify(depositPayoutBatchProcessor).processBatch(2L);
        verify(depositPayoutBatchProcessor).markBatchFailed(2L);
        verify(depositPayoutBatchProcessor).processBatch(3L);
    }
}
