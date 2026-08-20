package me.nawa.ingest.dto.response;

import lombok.Getter;

/**
 * 적재 결과입니다. 파이프라인이 이 값을 지표로 기록하고 리포트에 싣습니다.
 *
 * <p>skipped 는 본체가 아직 없어 붙이지 못한 번역 수입니다. 0 이 아니면
 * 본체 적재가 먼저 끝나지 않았다는 뜻이라 리포트에서 확인할 값입니다.
 */
@Getter
public class IngestResultResponse {

    private final int received;
    private final int inserted;
    private final int updated;
    private final int skipped;

    public IngestResultResponse(int received, int inserted, int updated, int skipped) {
        this.received = received;
        this.inserted = inserted;
        this.updated = updated;
        this.skipped = skipped;
    }
}
