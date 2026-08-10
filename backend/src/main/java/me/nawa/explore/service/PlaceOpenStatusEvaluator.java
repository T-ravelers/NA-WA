package me.nawa.explore.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlaceOpenStatusEvaluator {

    private static final Pattern TIME_RANGE = Pattern.compile(
        "(?<!\\d)([01]?\\d|2[0-4]):([0-5]\\d)\\s*[~～-]\\s*"
            + "([01]?\\d|2[0-4]):([0-5]\\d)(?!\\d)"
    );
    private static final Map<DayOfWeek, String> KOREAN_DAYS = Map.of(
        DayOfWeek.MONDAY, "월요일",
        DayOfWeek.TUESDAY, "화요일",
        DayOfWeek.WEDNESDAY, "수요일",
        DayOfWeek.THURSDAY, "목요일",
        DayOfWeek.FRIDAY, "금요일",
        DayOfWeek.SATURDAY, "토요일",
        DayOfWeek.SUNDAY, "일요일"
    );

    public boolean isOpen(
        JsonNode openingHours,
        JsonNode closedDays,
        ZonedDateTime now
    ) {
        if (isRegularlyClosed(closedDays, now.getDayOfWeek())) {
            return false;
        }
        String raw = rawOpeningHours(openingHours);
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        if (raw.contains("상시 개방") || raw.contains("24시간")) {
            return true;
        }

        String relevant = selectRelevantSchedule(raw, now.getDayOfWeek());
        if (!StringUtils.hasText(relevant)) {
            return false;
        }
        Matcher matcher = TIME_RANGE.matcher(relevant);
        if (!matcher.find()) {
            return false;
        }

        LocalTime open = parseTime(matcher.group(1), matcher.group(2));
        LocalTime close = parseTime(matcher.group(3), matcher.group(4));
        if (open == null || close == null) {
            return false;
        }
        boolean openNow = contains(open, close, now.toLocalTime());
        if (!openNow) {
            return false;
        }

        String remainder = relevant.substring(matcher.end()).toLowerCase(Locale.ROOT);
        Matcher breakMatcher = TIME_RANGE.matcher(remainder);
        if ((remainder.contains("브레이크") || remainder.contains("준비"))
            && breakMatcher.find()) {
            LocalTime breakStart = parseTime(
                breakMatcher.group(1), breakMatcher.group(2)
            );
            LocalTime breakEnd = parseTime(
                breakMatcher.group(3), breakMatcher.group(4)
            );
            if (breakStart != null
                && breakEnd != null
                && contains(breakStart, breakEnd, now.toLocalTime())) {
                return false;
            }
        }
        return true;
    }

    private boolean isRegularlyClosed(JsonNode closedDays, DayOfWeek day) {
        if (closedDays == null || !closedDays.isArray()) {
            return false;
        }
        String currentDay = KOREAN_DAYS.get(day);
        for (JsonNode item : closedDays) {
            String value = item.asText("");
            if (value.contains("연중무휴")) {
                continue;
            }
            if (value.contains("매주") && value.contains(currentDay)) {
                return true;
            }
            if (day == DayOfWeek.SATURDAY && value.contains("매주 주말")) {
                return true;
            }
            if (day == DayOfWeek.SUNDAY && value.contains("매주 주말")) {
                return true;
            }
        }
        return false;
    }

    private String rawOpeningHours(JsonNode openingHours) {
        if (openingHours == null || openingHours.isNull()) {
            return null;
        }
        if (openingHours.isTextual()) {
            return openingHours.asText();
        }
        JsonNode raw = openingHours.get("raw");
        return raw == null || raw.isNull() ? null : raw.asText();
    }

    private String selectRelevantSchedule(String raw, DayOfWeek day) {
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        if (raw.contains("평일") && raw.contains("주말")) {
            String marker = weekend ? "주말" : "평일";
            String other = weekend ? "평일" : "주말";
            int start = raw.indexOf(marker);
            int otherIndex = raw.indexOf(other, start + marker.length());
            return otherIndex < 0 ? raw.substring(start) : raw.substring(start, otherIndex);
        }
        String dayName = KOREAN_DAYS.get(day);
        if (raw.contains("요일") && !raw.contains(dayName)) {
            return null;
        }
        return raw;
    }

    private LocalTime parseTime(String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        int minute = Integer.parseInt(minuteText);
        if (hour == 24 && minute == 0) {
            return LocalTime.MIDNIGHT;
        }
        if (hour == 24) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private boolean contains(LocalTime start, LocalTime end, LocalTime time) {
        if (start.equals(end)) {
            return true;
        }
        if (end.isAfter(start)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }
}
