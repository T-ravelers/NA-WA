package me.nawa.explore.service;

import java.util.Locale;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import org.springframework.util.StringUtils;

/**
 * Explore 요청 언어를 번역 테이블이 저장한 형태로 맞춘다.
 *
 * <p>{@code event_translations}·{@code place_translations}의 {@code language_code}는
 * {@code CHECK (language_code IN ('en','ja','zh-TW','vi'))}라 <b>{@code zh-TW}를 대소문자
 * 그대로 저장한다.</b> 예전에는 요청 값을 통째로 {@code toLowerCase}해 {@code zh-tw}를
 * 넘겼다. 그래서 소문자로 접는 것은 <b>비교할 때뿐</b>이고, 돌려주는 값은 저장된 표기다.
 *
 * <p><b>이 정규화가 지금 장애의 원인은 아니었다.</b> 두 번역 테이블이
 * {@code utf8mb4_0900_ai_ci}라 {@code zh-tw = 'zh-TW'}가 참이어서 소문자로도 조인은 됐다.
 * "어떤 언어로 요청해도 한국어만 나온다"의 원인은 조회 SQL에 <b>조인 자체가 없던 것</b>이다.
 * 그래도 표기를 되돌리는 이유는 대비다 — collation을 {@code _bin}이나 {@code _as_cs}로
 * 바꾸거나 언어 코드를 자바에서 비교하는 코드가 생기는 순간 소문자 값은 조용히 어긋난다.
 *
 * <p>목록과 상세가 이 함수 하나를 함께 쓴다. 두 곳이 따로 정규화하면 같은 요청에 서로 다른
 * 언어가 나가는데, 그 어긋남은 화면을 열어 보기 전까지 드러나지 않는다.
 *
 * <p>이 목록은 <b>조회 쪽만</b> 담당한다. 적재 쪽은 {@code IngestServiceImpl.LANGUAGES}가
 * 같은 집합을 따로 들고 있어, 로케일을 늘릴 때는 DB {@code CHECK}까지 세 벌을 함께 넓혀야
 * 한다. 순서는 backend/docs/EXPLORE_API.md 의 「새 로케일을 추가하는 순서」에 있다.
 */
final class ExploreLanguagePolicy {

    /**
     * 크롤링 원본이 한국어라 번역이 없으면 그쪽으로 폴백한다. 언어를 아예 안 보낸 요청의
     * 기본값은 서비스 기본 로케일과 같은 {@code en}이다.
     */
    private static final String DEFAULT_LANGUAGE = "en";

    private ExploreLanguagePolicy() {
    }

    /**
     * 요청 언어를 번역 테이블의 표기로 정규화한다.
     *
     * @param language 요청이 보낸 값. 비어 있으면 {@code en}으로 본다
     * @return {@code en}, {@code ja}, {@code zh-TW}, {@code vi} 중 하나
     * @throws BusinessException 지원 목록 밖의 값이면 {@code COMMON-001}(400)
     */
    static String normalize(String language) {
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
