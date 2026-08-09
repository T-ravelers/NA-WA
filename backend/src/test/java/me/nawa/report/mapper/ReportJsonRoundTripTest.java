package me.nawa.report.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import me.nawa.explore.mapper.JsonNodeTypeHandler;
import org.junit.jupiter.api.Test;

class ReportJsonRoundTripTest {

    @Test
    void reportContentTypeHandler_roundTripsStoredJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode original = objectMapper.readTree("""
            {
              "journey": {
                "tripId": 1,
                "title": "Seoul Foodie Week",
                "startDate": "2026-08-01",
                "endDate": "2026-08-05"
              },
              "days": []
            }
            """);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        JsonNodeTypeHandler typeHandler = new JsonNodeTypeHandler();

        typeHandler.setNonNullParameter(
            preparedStatement,
            1,
            original,
            null
        );
        when(resultSet.getString("report_content"))
            .thenReturn(original.toString());

        JsonNode restored = typeHandler.getNullableResult(
            resultSet,
            "report_content"
        );

        assertEquals(original, restored);
        verify(preparedStatement).setString(1, original.toString());
    }
}
