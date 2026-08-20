package me.nawa.ingest.dto.response;

import lombok.Getter;

/**
 * 적재 결과입니다. 파이프라인이 이 값을 지표로 기록하고 리포트에 싣습니다.
 *
 * <p>skipped 는 본체가 아직 없어 붙이지 못한 번역 수입니다. 0 이 아니면
 * 본체 적재가 먼저 끝나지 않았다는 뜻이라 리포트에서 확인할 값입니다.
 *
 * <p><b>번역은 inserted 를 구분하지 않습니다.</b> 신규도 updated 로 셉니다.
 * MySQL 의 ON DUPLICATE KEY UPDATE 가 갱신된 행을 2로 세어 affected 만으로는
 * 신규와 갱신을 가를 수 없기 때문입니다. 굳이 가르려면 조회를 한 번 더 해야
 * 하는데, 리포트에 필요한 값은 "몇 건이 반영됐고 몇 건이 빠졌나"입니다.
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
