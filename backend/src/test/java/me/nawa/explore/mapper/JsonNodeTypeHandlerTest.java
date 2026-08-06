package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.ResultSet;
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
    void getNullableResult_throwsTypeException_whenJsonIsInvalid()
        throws Exception {
        when(resultSet.getString("links")).thenReturn("not-json");

        assertThrows(
            TypeException.class,
            () -> typeHandler.getNullableResult(resultSet, "links")
        );
    }
}
