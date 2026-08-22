package me.nawa.report.domain;

/**
 * 비교 숫자의 출처.
 *
 * <p>{@code LIVE}는 여정 기간의 결제를 지금 다시 합산한 것이고, {@code SNAPSHOT}은 저장된
 * 리포트 스냅샷을 그대로 읽은 것이다. 응답에 이름 그대로 실리므로 값 이름이 곧 API 계약이다.</p>
 */
public enum ReportComparisonBasis {
    LIVE,
    SNAPSHOT
}
