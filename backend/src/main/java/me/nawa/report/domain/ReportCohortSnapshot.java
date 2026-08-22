package me.nawa.report.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 코호트 회원 한 명의 최신 리포트 스냅샷 중 {@code analytics} 부분.
 *
 * <p>analytics가 없던 시절의 스냅샷은 {@code analytics}가 null로 온다. 서비스가 걸러낸다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportCohortSnapshot {

    private Long memberId;
    private JsonNode analytics;
}
