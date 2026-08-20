package me.nawa.ingest.exception;

import me.nawa.common.exception.BusinessException;

/**
 * DB 제약을 어길 항목이 배치에 섞여 있을 때 던집니다.
 *
 * <p>왜 서비스에서 미리 보는가: 제약 위반이 SQLException 으로 터지면 500 이 나가고,
 * 파이프라인은 그것을 일시적 장애로 보아 같은 배치를 매일 다시 보냅니다. 잘못된
 * 한 건이 그 배치를 영원히 막습니다.
 *
 * <p>4xx 로 돌려주면 파이프라인이 "고쳐서 보내야 하는 것"으로 구분할 수 있습니다.
 */
public class IngestInvalidItemException extends BusinessException {

    public IngestInvalidItemException() {
        super(IngestErrorCode.INVALID_ITEM);
    }
}
