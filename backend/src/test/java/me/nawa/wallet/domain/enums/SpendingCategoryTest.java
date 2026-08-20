package me.nawa.wallet.domain.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SpendingCategoryTest {

    private final Locale originalLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void from_returnsSameCategory_whenValueIsAllowed() {
        for (SpendingCategory category : SpendingCategory.values()) {
            assertEquals(category, SpendingCategory.from(category.name()));
        }
    }

    @Test
    void from_normalizesCaseAndWhitespace() {
        assertEquals(SpendingCategory.FOOD, SpendingCategory.from("food"));
        assertEquals(SpendingCategory.FOOD, SpendingCategory.from("Food"));
        assertEquals(SpendingCategory.FOOD, SpendingCategory.from("  FOOD  "));
        assertEquals(SpendingCategory.SHOPPING, SpendingCategory.from("shopping"));
    }

    // 터키어 로케일에서 `i`는 `İ`(U+0130)로 올라가, 인자 없는 toUpperCase면 `shopping`이
    // `SHOPPİNG`이 되어 거부된다. 일곱 값 중 SHOPPING 하나가 `i`를 가지고 있다.
    @Test
    void from_normalizesIndependentlyOfDefaultLocale() {
        Locale.setDefault(new Locale("tr", "TR"));

        assertEquals(SpendingCategory.SHOPPING, SpendingCategory.from("shopping"));
    }

    @Test
    void from_returnsOther_whenValueIsNullOrBlank() {
        assertEquals(SpendingCategory.OTHER, SpendingCategory.from(null));
        assertEquals(SpendingCategory.OTHER, SpendingCategory.from(""));
        assertEquals(SpendingCategory.OTHER, SpendingCategory.from("   "));
    }

    // 저장된 값은 지나간 결제의 사실이라 되돌릴 수 없다. 여기서 거부하면 이미 성공한
    // 결제의 멱등 재시도가 400을 받는다.
    @Test
    void fromStored_foldsUnknownValueIntoOther() {
        assertEquals(SpendingCategory.OTHER, SpendingCategory.fromStored("CAFE"));
        assertEquals(SpendingCategory.OTHER, SpendingCategory.fromStored(null));
        assertEquals(SpendingCategory.FOOD, SpendingCategory.fromStored("FOOD"));
    }

    // 오타를 조용히 기타 소비로 삼키면 리포트 칭호가 틀린 근거로 만들어진다.
    @Test
    void from_throws_whenValueIsNotAllowed() {
        for (String value : new String[] {"CAFE", "FOOD_AND_DRINK", "기타"}) {
            BusinessException exception = assertThrows(
                BusinessException.class,
                () -> SpendingCategory.from(value)
            );

            assertEquals(
                WalletErrorCode.SPENDING_CATEGORY_NOT_ALLOWED,
                exception.getErrorCode()
            );
        }
    }
}
