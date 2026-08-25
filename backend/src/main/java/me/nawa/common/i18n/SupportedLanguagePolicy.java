package me.nawa.common.i18n;

import java.util.Locale;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import org.springframework.util.StringUtils;

/**
 * Explore·Journey가 공유하는 표시 언어 허용 목록.
 *
 * <p>{@code event_translations}·{@code place_translations}의 {@code language_code}는
 * {@code CHECK (language_code IN ('en','ja','zh-TW','vi'))}로 이 표기를 저장한다.
 * {@code zh-TW}는 대소문자를 보존해 돌려준다 — 두 번역 테이블의 collation이
 * {@code utf8mb4_0900_ai_ci}(대소문자 구분 없음)라 지금은 소문자로도 조인이 되지만,
 * collation을 {@code _bin}·{@code _as_cs}로 바꾸거나 언어 코드를 애플리케이션에서
 * 비교하는 코드가 생기면 소문자 값은 그때 조용히 어긋난다. 표기 보존은 그 대비다.
 *
 * <p>이 목록은 <b>조회 쪽만</b> 담당한다. 적재 쪽은 {@code IngestServiceImpl.LANGUAGES}가
 * 같은 집합을 따로 들고 있어, 로케일을 늘릴 때는 DB {@code CHECK}·조회·적재 세 벌을 함께
 * 넓혀야 한다. 순서는 backend/docs/EXPLORE_API.md 의 「새 로케일을 추가하는 순서」에 있다.
 *
 * <p>원래 {@code me.nawa.explore.service.ExploreLanguagePolicy}였다가, Journey
 * 타임라인이 같은 로직을 그대로 복사해 두 벌이 됐다(#536). Explore와 Journey는 둘 다
 * "저장된 번역을 조회해 표시 언어를 고른다"는 같은 책임이라 여기로 옮겼다 — 적재
 * 쪽과는 책임이 달라 옮기지 않는다.
 */
public final class SupportedLanguagePolicy {

    /**
     * 크롤링 원본이 한국어라 번역이 없으면 그쪽으로 폴백한다. 언어를 아예 안 보낸 요청의
     * 기본값은 서비스 기본 로케일과 같은 {@code en}이다.
     */
    private static final String DEFAULT_LANGUAGE = "en";

    private SupportedLanguagePolicy() {
    }

    /**
     * 요청 언어를 번역 테이블의 표기로 정규화한다.
     *
     * @param language 요청이 보낸 값. 비어 있으면 {@code en}으로 본다
     * @return {@code en}, {@code ja}, {@code zh-TW}, {@code vi} 중 하나
     * @throws BusinessException 지원 목록 밖의 값이면 {@code COMMON-001}(400)
     */
    public static String normalize(String language) {
        if (!StringUtils.hasText(language)) {
            return DEFAULT_LANGUAGE;
        }

        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "en" -> "en";
            case "ja" -> "ja";
            case "zh-tw" -> "zh-TW";
            case "vi" -> "vi";
            default -> throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        };
    }
}
