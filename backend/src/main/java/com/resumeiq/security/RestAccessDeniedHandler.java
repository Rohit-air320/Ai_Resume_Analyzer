package com.resumeiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.common.api.ApiErrorResponse;
import com.resumeiq.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The 403 for a caller who is signed in but not allowed.
 *
 * <p>Rare by design. Ownership in this application is enforced by the repository signatures —
 * a resume that belongs to somebody else is not found rather than found and refused — so this
 * fires only for role-gated endpoints. It is logged at warn level because, unlike a 401, it
 * means a real account tried something outside its remit.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException deniedException) throws IOException {

        log.warn("Access denied on {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(ErrorCode.FORBIDDEN.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(
                        ErrorCode.FORBIDDEN, "You do not have access to that.", request.getRequestURI()));
    }
}
