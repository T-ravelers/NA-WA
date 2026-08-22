package me.nawa.report.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 내 카테고리 한 칸의 순위. {@code rank}는 1부터, {@code of}는 나를 포함한 인원이다. */
@Getter
@Builder
public class ReportComparisonRankResponse {

    private String category;
    private int rank;
    private int of;
}
