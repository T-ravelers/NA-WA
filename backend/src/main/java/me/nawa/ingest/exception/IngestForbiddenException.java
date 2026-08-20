package me.nawa.ingest.exception;

import me.nawa.common.exception.BusinessException;

/**
 * SYSTEM 계정이 아닌 토큰으로 적재를 시도할 때 던집니다.
 */
public class IngestForbiddenException extends BusinessException {

    public IngestForbiddenException() {
        super(IngestErrorCode.FORBIDDEN);
    }
}
