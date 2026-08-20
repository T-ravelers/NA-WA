package me.nawa.wallet.domain.enums;

import java.util.Arrays;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.exception.WalletErrorCode;

/**
 * 소비 카테고리.
 *
 * `wallet_transfers.spending_category`에 저장하는 값의 전체 집합이다. 컬럼이
 * `VARCHAR(20) NULL` 자유 문자열이라 DB가 값을 막아주지 않으므로, 여기가 유일한
 * allow-list다. 목록 밖의 값은 저장 경로에서 거부한다.
 *
 * 앞의 넷은 Explore 소비영역(`beauty`·`shopping`·`show`·`food`)과 같은 어휘를 쓴다.
 * 다만 타입은 공유하지 않는다 — Explore는 탐색 아이템의 분류고 이쪽은 결제 건의
 * 분류라, 한쪽을 늘리는 결정이 다른 쪽을 끌고 가면 안 된다.
 *
 * 값 집합과 칭호 매핑은 `backend/docs/SPENDING_CATEGORY.md`가 정본이다.
 */
public enum SpendingCategory {
    FOOD,
    SHOPPING,
    BEAUTY,
    SHOW,
    TRANSPORT,
    STAY,
    OTHER;

    /**
     * 요청으로 들어온 문자열을 카테고리로 바꾼다.
     *
     * 값을 고르지 않은 결제도 받아야 하므로 null과 빈 문자열은 {@link #OTHER}로 접는다.
     * 리포트 집계 쿼리가 이미 `COALESCE(NULLIF(spending_category, ''), 'OTHER')`로 같은
     * 접기를 하므로, 저장 시점에 접어 두면 두 곳의 결과가 어긋나지 않는다.
     *
     * 목록 밖의 값은 조용히 {@link #OTHER}로 만들지 않고 거부한다. 오타를 기타 소비로
     * 삼키면 리포트 칭호가 틀린 근거로 만들어진다.
     */
    public static SpendingCategory from(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }

        String normalized = value.trim().toUpperCase();

        return Arrays.stream(values())
            .filter(category -> category.name().equals(normalized))
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                WalletErrorCode.SPENDING_CATEGORY_NOT_ALLOWED
            ));
    }
}
