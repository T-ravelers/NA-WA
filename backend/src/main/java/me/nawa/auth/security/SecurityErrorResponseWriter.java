package me.nawa.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.nawa.common.exception.ErrorCode;
import me.nawa.common.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void write(
            HttpServletResponse response,
            ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(errorCode)
        );
    }
}
