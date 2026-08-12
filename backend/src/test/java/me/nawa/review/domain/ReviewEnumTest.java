package me.nawa.review.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ReviewEnumTest {

    @Test
    void reviewCategory_containsSupportedScoreCategories() {
        assertArrayEquals(
                new ReviewCategory[]{
                        ReviewCategory.PUNCTUALITY,
                        ReviewCategory.MANNERS,
                        ReviewCategory.COMMUNICATION
                },
                ReviewCategory.values()
        );
    }

    @Test
    void reviewKeywordCode_containsSeededKeywords() {
        assertArrayEquals(
                new ReviewKeywordCode[]{
                        ReviewKeywordCode.FRIENDLY,
                        ReviewKeywordCode.ON_TIME,
                        ReviewKeywordCode.CONSIDERATE,
                        ReviewKeywordCode.GOOD_COMMUNICATOR,
                        ReviewKeywordCode.WOULD_JOIN_AGAIN
                },
                ReviewKeywordCode.values()
        );
    }
}
