package me.nawa.wallet.dto.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import me.nawa.wallet.domain.enums.SpendingScope;
import org.junit.jupiter.api.Test;

class QrPaymentResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private static final LocalDateTime DATE_TIME =
        LocalDateTime.of(2026, 8, 12, 15, 30);

    @Test
    void serializesExpiresAtAsIsoString() throws Exception {
        JsonNode create = objectMapper.readTree(objectMapper.writeValueAsString(
            new QrPaymentCreateResponse(1L, "token", BigDecimal.ONE, null, "ACTIVE", "KRW", DATE_TIME)
        ));
        JsonNode resolve = objectMapper.readTree(objectMapper.writeValueAsString(
            new QrPaymentResolveResponse(1L, "Jieun", BigDecimal.ONE, false, null, "ACTIVE", "KRW", DATE_TIME)
        ));
        JsonNode preview = objectMapper.readTree(objectMapper.writeValueAsString(
            new QrPaymentPreviewResponse(1L, "Jieun", BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.valueOf(9), "KRW", SpendingScope.PERSONAL, null, null, true, DATE_TIME)
        ));

        assertIsoString(create, "expiresAt");
        assertIsoString(resolve, "expiresAt");
        assertIsoString(preview, "expiresAt");
    }

    @Test
    void serializesCompletedAtAsIsoString() throws Exception {
        JsonNode execute = objectMapper.readTree(objectMapper.writeValueAsString(
            new QrPaymentExecuteResponse(1L, 2L, "COMPLETED", BigDecimal.ONE,
                BigDecimal.TEN, "KRW", DATE_TIME)
        ));
        JsonNode status = objectMapper.readTree(objectMapper.writeValueAsString(
            new QrPaymentStatusResponse(1L, "COMPLETED", BigDecimal.ONE,
                BigDecimal.TEN, "KRW", DATE_TIME)
        ));

        assertIsoString(execute, "completedAt");
        assertIsoString(status, "completedAt");
    }

    private void assertIsoString(JsonNode response, String fieldName) {
        assertTrue(response.get(fieldName).isTextual());
        assertEquals("2026-08-12T15:30:00", response.get(fieldName).asText());
    }
}
