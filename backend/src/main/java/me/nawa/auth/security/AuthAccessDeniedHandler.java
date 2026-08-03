package me.nawa.auth.security;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityErrorResponseWriter errorResponseWriter;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        AuthErrorCode errorCode = isCsrfFailure(accessDeniedException)
                ? AuthErrorCode.INVALID_CSRF_TOKEN
                : AuthErrorCode.ACCESS_DENIED;
        errorResponseWriter.write(response, errorCode);
    }

    private boolean isCsrfFailure(
            AccessDeniedException accessDeniedException) {
        return accessDeniedException instanceof MissingCsrfTokenException
                || accessDeniedException
                instanceof InvalidCsrfTokenException;
    }
}
