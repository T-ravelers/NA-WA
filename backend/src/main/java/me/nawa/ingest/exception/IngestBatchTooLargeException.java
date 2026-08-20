package me.nawa.ingest.exception;

import me.nawa.common.exception.BusinessException;

/**
 * 한 요청에 담긴 건수가 상한을 넘을 때 던집니다.
 *
 * <p>상한을 두는 이유는 트랜잭션 하나가 지나치게 길어지면 운영 조회가 함께
 * 느려지기 때문입니다. 파이프라인은 나눠 보내면 됩니다.
 */
public class IngestBatchTooLargeException extends BusinessException {

    public IngestBatchTooLargeException() {
        super(IngestErrorCode.BATCH_TOO_LARGE);
    }
}
