package me.nawa.journey.service;

import java.util.Locale;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import org.springframework.util.StringUtils;

final class JourneyLanguagePolicy {

    private static final String DEFAULT_LANGUAGE = "en";

    private JourneyLanguagePolicy() {
    }

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
