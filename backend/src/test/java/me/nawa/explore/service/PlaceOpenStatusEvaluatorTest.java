package me.nawa.explore.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class PlaceOpenStatusEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlaceOpenStatusEvaluator evaluator =
        new PlaceOpenStatusEvaluator();

    @Test
    void isOpen_returnsTrue_forSimpleRawHours() throws Exception {
        assertTrue(evaluator.isOpen(
            objectMapper.readTree("{\"raw\":\"11:00~21:00\"}"),
            objectMapper.readTree("[\"연중무휴\"]"),
            atSeoul(2026, 8, 10, 13, 0)
        ));
    }

    @Test
    void isOpen_returnsFalse_duringBreakTime() throws Exception {
        assertFalse(evaluator.isOpen(
            objectMapper.readTree(
                "{\"raw\":\"11:00~21:00 브레이크타임 15:00~17:00\"}"
            ),
            objectMapper.readTree("[\"연중무휴\"]"),
            atSeoul(2026, 8, 10, 16, 0)
        ));
    }

    @Test
    void isOpen_returnsFalse_onRegularClosedDay() throws Exception {
        assertFalse(evaluator.isOpen(
            objectMapper.readTree("{\"raw\":\"11:00~21:00\"}"),
            objectMapper.readTree("[\"매주 월요일\"]"),
            atSeoul(2026, 8, 10, 13, 0)
        ));
    }

    @Test
    void isOpen_supportsOvernightHours() throws Exception {
        assertTrue(evaluator.isOpen(
            objectMapper.readTree("{\"raw\":\"18:00~02:00\"}"),
            objectMapper.readTree("[\"연중무휴\"]"),
            atSeoul(2026, 8, 10, 1, 0)
        ));
    }

    @Test
    void isOpen_returnsFalse_whenHoursCannotBeInterpreted() throws Exception {
        assertFalse(evaluator.isOpen(
            objectMapper.readTree("{\"raw\":\"전화 문의\"}"),
            objectMapper.readTree("[]"),
            atSeoul(2026, 8, 10, 13, 0)
        ));
    }

    private ZonedDateTime atSeoul(
        int year, int month, int day, int hour, int minute
    ) {
        return ZonedDateTime.of(
            year, month, day, hour, minute, 0, 0,
            ZoneId.of("Asia/Seoul")
        );
    }
}
