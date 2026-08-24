package me.nawa.journey.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

class JourneyLanguagePolicyTest {

    @Test
    void normalize_defaultsBlankLanguageToEnglish() {
        assertEquals("en", JourneyLanguagePolicy.normalize(null));
        assertEquals("en", JourneyLanguagePolicy.normalize("  "));
    }

    @Test
    void normalize_preservesSupportedDatabaseLanguageCodes() {
        assertEquals("en", JourneyLanguagePolicy.normalize("EN"));
        assertEquals("ja", JourneyLanguagePolicy.normalize(" ja "));
        assertEquals("zh-TW", JourneyLanguagePolicy.normalize("zh-tw"));
        assertEquals("vi", JourneyLanguagePolicy.normalize("VI"));
    }

    @Test
    void normalize_rejectsUnsupportedLanguage() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> JourneyLanguagePolicy.normalize("ko")
        );

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
    }
}
