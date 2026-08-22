package me.nawa.report.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 리포트 비교 응답. 숫자는 전부 0 이상이고 문구는 싣지 않는다 — 차이의 부호와 표현은
 * 프론트엔드가 비중으로 계산한다.
 *
 * <p>{@code basis}는 숫자의 출처다. {@code LIVE}는 여정 기간의 결제를 지금 다시 합산한 것,
 * {@code SNAPSHOT}은 저장된 리포트 스냅샷을 그대로 읽은 것이다.</p>
 */
@Getter
@Builder
public class ReportComparisonResponse {

    private String scope;
    private String basis;
    private ReportComparisonMemberResponse me;
    private List<ReportComparisonMemberResponse> peers;
    private ReportComparisonCohortResponse cohort;
    private List<ReportComparisonRankResponse> ranks;
}
