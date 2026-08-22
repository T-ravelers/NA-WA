package me.nawa.report.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원 한 명의 카테고리 한 칸 합계. DB가 GROUP BY로 만들어 준 행이다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportComparisonSpending {

    private Long memberId;
    private String category;
    private BigDecimal amount;
}
