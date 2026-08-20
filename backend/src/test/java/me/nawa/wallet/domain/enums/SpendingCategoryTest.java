package me.nawa.wallet.domain.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

class SpendingCategoryTest {

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
    }

    @Test
    void from_returnsOther_whenValueIsNullOrBlank() {
        assertEquals(SpendingCategory.OTHER, SpendingCategory.from(null));
        assertEquals(SpendingCategory.OTHER, SpendingCategory.from(""));
        assertEquals(SpendingCategory.OTHER, SpendingCategory.from("   "));
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
