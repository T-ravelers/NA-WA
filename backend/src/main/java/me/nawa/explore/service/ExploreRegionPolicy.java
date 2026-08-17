package me.nawa.explore.service;

import java.util.List;

final class ExploreRegionPolicy {

    private static final List<String> SEOUL_REGION2_VALUES = List.of(
        "가락·문정", "강남", "건대", "공덕", "공릉", "구로·가산", "노량진", "노원",
        "대학로", "동대문·DDP", "마곡·김포공항", "망원", "명동", "목동", "반포",
        "봉천·서울대", "부암·평창", "삼성·코엑스", "상암", "서초", "서촌",
        "성북·한성대", "성수", "송리단길", "수유·미아", "신림", "신촌",
        "압구정·도산", "여의도", "연남", "연희", "용산", "인사동·북촌", "잠실",
        "창동·도봉", "천호·강동", "청담", "청량리·회기", "합정", "한남·이태원", "홍대"
    );

    private ExploreRegionPolicy() {
    }

    static List<String> knownRegion2Values(List<String> region1) {
        return region1 != null && region1.size() == 1 && "서울".equals(region1.get(0))
            ? SEOUL_REGION2_VALUES : List.of();
    }
}
