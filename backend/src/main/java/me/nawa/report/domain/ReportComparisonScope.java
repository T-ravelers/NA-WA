package me.nawa.report.domain;

/**
 * 리포트 비교 범위.
 *
 * <p>{@code GROUP}은 리포트 여정에 묶인 약속의 다른 참가자와, {@code SIMILAR}는 같은 국적의
 * 다른 회원과 비교한다. 쿼리 파라미터로 그대로 받으므로 값 이름이 곧 API 계약이다.</p>
 */
public enum ReportComparisonScope {
    GROUP,
    SIMILAR
}
