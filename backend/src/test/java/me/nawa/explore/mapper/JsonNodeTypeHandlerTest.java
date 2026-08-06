package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonNodeTypeHandlerTest {

    private final JsonNodeTypeHandler typeHandler = new JsonNodeTypeHandler();

    @Mock
    private ResultSet resultSet;

    @Mock
    private CallableStatement callableStatement;

    @Mock
    private PreparedStatement preparedStatement;

    @Test
    void getNullableResult_parsesJsonArray() throws Exception {
        when(resultSet.getString("image_urls"))
            .thenReturn("[\"https://example.com/image.jpg\"]");

        JsonNode result = typeHandler.getNullableResult(resultSet, "image_urls");

        assertEquals("https://example.com/image.jpg", result.get(0).asText());
    }

    @Test
    void getNullableResult_parsesJsonObject() throws Exception {
        when(resultSet.getString("links"))
            .thenReturn("{\"homepage_url\":\"https://example.com\"}");

        JsonNode result = typeHandler.getNullableResult(resultSet, "links");

        assertEquals("https://example.com", result.path("homepage_url").asText());
    }

    @Test
    void getNullableResult_returnsNull_whenColumnIsNull() throws Exception {
        when(resultSet.getString("pre_reservation")).thenReturn(null);

        JsonNode result = typeHandler.getNullableResult(
            resultSet,
            "pre_reservation"
        );

        assertNull(result);
    }

    @Test
    void getNullableResult_returnsNull_whenColumnIsBlank() throws Exception {
        when(resultSet.getString("links")).thenReturn("   ");

        JsonNode result = typeHandler.getNullableResult(resultSet, "links");

        assertNull(result);
    }

    @Test
    void getNullableResult_parsesJsonArray_whenColumnIndexIsUsed()
        throws Exception {
        when(resultSet.getString(1)).thenReturn("[\"first\",\"second\"]");

        JsonNode result = typeHandler.getNullableResult(resultSet, 1);

        assertEquals("second", result.get(1).asText());
    }

    @Test
    void getNullableResult_parsesJsonObject_fromCallableStatement()
        throws Exception {
        when(callableStatement.getString(1))
            .thenReturn("{\"has\":true}");

        JsonNode result = typeHandler.getNullableResult(callableStatement, 1);

        assertEquals(true, result.path("has").asBoolean());
    }

    @Test
    void setNonNullParameter_serializesJson() throws Exception {
        JsonNode parameter = new ObjectMapper().readTree(
            "{\"homepageUrl\":\"https://example.com\"}"
        );

        typeHandler.setNonNullParameter(
            preparedStatement,
            1,
            parameter,
            JdbcType.OTHER
        );

        verify(preparedStatement).setString(
            1,
            "{\"homepageUrl\":\"https://example.com\"}"
        );
    }

    @Test
    void getNullableResult_throwsTypeException_whenJsonIsInvalid()
        throws Exception {
        when(resultSet.getString("links")).thenReturn("not-json");

        assertThrows(
            TypeException.class,
            () -> typeHandler.getNullableResult(resultSet, "links")
        );
    }
}
