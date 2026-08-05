package me.nawa.wallet.dto.response;

import java.util.List;

public record TopupListResponse(
    List<TopupSummaryResponse> topups, // 이번 페이지 충전 내역 목록
    String nextCursor                  // 다음 페이지 커서 (더 없으면 null)
) {

    public static TopupListResponse of(List<TopupSummaryResponse> topups, String nextCursor) {
        return new TopupListResponse(topups, nextCursor);
    }
}
