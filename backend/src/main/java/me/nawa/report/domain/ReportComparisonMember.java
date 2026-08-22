package me.nawa.report.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 비교에 등장하는 회원 한 명. 표시 정보와 코호트 기준(국적)만 담는다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportComparisonMember {

    private Long memberId;
    private String displayName;
    private String profileImageUrl;
    private String nationalityCode;
}
