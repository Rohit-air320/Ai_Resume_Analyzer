package com.resumeiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.common.api.ApiErrorResponse;
import com.resumeiq.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The 401 for requests that reach a protected endpoint without a usable identity.
 *
 * <p>Without this, Spring Security answers with its own empty 401 and a
 * {@code WWW-Authenticate} header, which in a browser triggers the native basic-auth
 * dialog — a modal the frontend cannot dismiss and did not ask for. Writing
 * {@link ApiErrorResponse} instead means an expired token looks like every other error
 * this API produces, so the client has one parser and one code path.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String failure = (String) request.getAttribute(JwtAuthenticationFilter.FAILURE_ATTRIBUTE);
        String message = switch (failure == null ? "" : failure) {
            case JwtAuthenticationFilter.FAILURE_EXPIRED ->
                    "Your session has expired. Please sign in again.";
            case JwtAuthenticationFilter.FAILURE_INVALID ->
                    "We could not verify your sign-in. Please sign in again.";
            default -> "Please sign in to continue.";
        };

        response.setStatus(ErrorCode.UNAUTHORIZED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(ErrorCode.UNAUTHORIZED, message, request.getRequestURI()));
    }
}
