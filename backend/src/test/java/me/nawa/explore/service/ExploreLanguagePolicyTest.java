package me.nawa.explore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ExploreLanguagePolicyTest {

    /**
     * 예전 정규화는 요청 값을 통째로 소문자로 접어 {@code zh-TW}를 {@code zh-tw}로 바꿨다.
     *
     * <p><b>그 자체가 장애의 원인은 아니었다</b> — 번역 테이블 collation이
     * {@code utf8mb4_0900_ai_ci}라 소문자로도 조인이 됐다. 표기를 되돌리는 것은 대비다.
     * collation이 대소문자를 가리게 바뀌거나 언어 코드를 자바에서 비교하는 코드가 생기면
     * 소문자 값은 그때 조용히 어긋난다.
     */
    @Test
    @DisplayName("zh-TW는 소문자로 접지 않고 저장된 표기 그대로 돌려준다")
    void normalize_preservesZhTwCasing() {
        assertEquals("zh-TW", ExploreLanguagePolicy.normalize("zh-TW"));
    }

    @ParameterizedTest
    @DisplayName("대소문자와 앞뒤 공백이 어떻게 들어와도 저장된 표기로 맞춘다")
    @CsvSource({
        "en, en",
        "EN, en",
        "'  en  ', en",
        "ja, ja",
        "JA, ja",
        "zh-tw, zh-TW",
        "ZH-TW, zh-TW",
        "Zh-Tw, zh-TW",
        "'  zh-TW  ', zh-TW",
        "vi, vi",
        "VI, vi",
    })
    void normalize_mapsSupportedLanguagesToStoredForm(
        String given,
        String expected
    ) {
        assertEquals(expected, ExploreLanguagePolicy.normalize(given));
    }

    @ParameterizedTest
    @DisplayName("언어를 보내지 않으면 서비스 기본 로케일인 en으로 본다")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void normalize_defaultsToEnglish_whenAbsent(String given) {
        assertEquals("en", ExploreLanguagePolicy.normalize(given));
    }

    /**
     * 지원 목록 밖의 값을 조용히 받아 주면 번역이 없는 언어로 조인해 한국어가 나가는데,
     * 사용자에게는 "번역이 아직 없다"와 "언어 코드를 잘못 보냈다"가 똑같아 보인다.
     * 한국어는 서비스 로케일이 아니므로 {@code ko}도 여기서 거절한다.
     */
    @ParameterizedTest
    @DisplayName("지원하지 않는 언어는 COMMON-001로 거절한다")
    @ValueSource(strings = {"ko", "zh", "zh-CN", "fr", "en-US", "english"})
    void normalize_rejectsUnsupportedLanguages(String given) {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> ExploreLanguagePolicy.normalize(given)
        );

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
    }
}
