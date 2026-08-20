package me.nawa.wallet.domain.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
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
        return resolve(value).orElseThrow(() -> new BusinessException(
            WalletErrorCode.SPENDING_CATEGORY_NOT_ALLOWED
        ));
    }

    /**
     * 이미 저장된 값을 읽는다.
     *
     * {@link #from}과 달리 목록 밖의 값을 거부하지 않고 {@link #OTHER}로 접는다. 저장된
     * 값은 지나간 결제의 사실이라 되돌릴 수 없는데, 여기서 거부하면 이미 성공한 결제의
     * 멱등 재시도가 멱등 응답 대신 400을 받는다. 값 집합을 줄이거나 백필이 한 번이라도
     * 들어오면 실제로 일어난다.
     *
     * 들어오는 값은 계속 거부한다. 두 방향을 다르게 다루는 것이 의도다.
     */
    public static SpendingCategory fromStored(String value) {
        return resolve(value).orElse(OTHER);
    }

    /**
     * 앞뒤 공백을 자르고 대문자로 맞춘 뒤 목록에서 찾는다.
     *
     * `toUpperCase`에 {@link Locale#ROOT}를 넘긴다. 인자가 없으면 JVM 기본 로케일을 따르는데,
     * 터키어 로케일에서는 `i`가 `İ`(U+0130)로 올라가 `shopping`이 `SHOPPİNG`이 된다. 일곱 값
     * 중 `SHOPPING` 하나가 `i`를 가지고 있어, 그 로케일에서 소문자 요청이 거부된다.
     */
    private static Optional<SpendingCategory> resolve(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(OTHER);
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        return Arrays.stream(values())
            .filter(category -> category.name().equals(normalized))
            .findFirst();
    }
}
