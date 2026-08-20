package me.nawa.ingest.exception;

import me.nawa.common.exception.BusinessException;

/**
 * 공유 비밀이 맞지 않을 때 던집니다.
 *
 * <p>어느 쪽이 틀렸는지 구분해 알려주지 않습니다. 비밀을 맞춰 보는 쪽에
 * 단서를 주지 않기 위해서입니다.
 */
public class IngestUnauthorizedException extends BusinessException {

    public IngestUnauthorizedException() {
        super(IngestErrorCode.UNAUTHORIZED);
    }
}
