package com.resumeiq.common.exception;

import com.resumeiq.common.api.ApiErrorResponse;
import com.resumeiq.common.api.ApiErrorResponse.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates every exception into {@link ApiErrorResponse}.
 *
 * <p>Two rules keep this class honest. Client mistakes (4xx) are logged at warn level
 * without a stack trace, because they are not bugs. Anything unanticipated is logged at
 * error level with the full trace, and the caller receives a generic apology — the user
 * never sees an exception class, a SQL fragment, or a file path.
 *
 * <p>Because this advice also catches {@link Exception}, Spring MVC's own exceptions are
 * handled explicitly first; otherwise a request to an unknown URL would be reported as an
 * internal error instead of a 404.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_MESSAGE = "Something went wrong on our side. Please try again.";

    /** Every deliberate application error arrives here already carrying its status. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        logByStatus(code, request, ex);
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Login throttling. The one handler that adds a header, because a client that knows how
     * long to wait can count down instead of retrying blindly.
     */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyAttempts(
            TooManyAttemptsException ex, HttpServletRequest request) {

        log.warn("Throttled {} {}: retry after {}s",
                request.getMethod(), request.getRequestURI(), ex.retryAfterSeconds());
        return ResponseEntity.status(ErrorCode.TOO_MANY_REQUESTS.status())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .body(ApiErrorResponse.of(
                        ErrorCode.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Spring Security's own exceptions, for the cases this advice can actually see.
     *
     * <p>Rejections by the filter chain never reach here — filters run outside the
     * {@code DispatcherServlet}, which is why {@code RestAuthenticationEntryPoint} and
     * {@code RestAccessDeniedHandler} exist. What does reach here is a check made inside a
     * controller or service, and without these two handlers the catch-all below would report
     * "access denied" as an internal error.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status())
                .body(ApiErrorResponse.of(
                        ErrorCode.UNAUTHORIZED,
                        "Please sign in to continue.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied on {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(ApiErrorResponse.of(
                        ErrorCode.FORBIDDEN,
                        "You do not have access to that.",
                        request.getRequestURI()));
    }

    /** @Valid failures on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                violations.add(new FieldViolation(error.getField(), messageOf(error.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                violations.add(new FieldViolation(error.getObjectName(), messageOf(error.getDefaultMessage()))));

        log.warn("Validation failed for {} {}: {} problem(s)",
                request.getMethod(), request.getRequestURI(), violations.size());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiErrorResponse.validation(
                        "Please correct the highlighted fields.", violations, request.getRequestURI()));
    }

    /** @Validated failures on path variables, request params, or @ConfigurationProperties. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        lastNodeOf(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();

        log.warn("Constraint violation on {} {}: {}",
                request.getMethod(), request.getRequestURI(), violations);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiErrorResponse.validation(
                        "Please correct the highlighted fields.", violations, request.getRequestURI()));
    }

    /** Malformed JSON, a wrongly typed path variable, or a missing required parameter. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        // Deliberately not ex.getMessage(): a Jackson parse error quotes the offending
        // part of the request body, which on this API can be resume text.
        log.warn("Malformed request {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.status())
                .body(ApiErrorResponse.of(
                        ErrorCode.BAD_REQUEST,
                        "The request could not be read. Please check the values you sent.",
                        request.getRequestURI()));
    }

    /** Upload larger than {@code spring.servlet.multipart.max-file-size}. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.warn("Upload rejected on {}: exceeds configured maximum", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.status())
                .body(ApiErrorResponse.of(
                        ErrorCode.FILE_TOO_LARGE,
                        "That file is too large. Please upload a resume under 5 MB.",
                        request.getRequestURI()));
    }

    /** Unique or foreign key violations that slipped past service-level checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.CONFLICT.status())
                .body(ApiErrorResponse.of(
                        ErrorCode.CONFLICT,
                        "That change conflicts with existing data.",
                        request.getRequestURI()));
    }

    /** Unknown path, unsupported verb, unsupported content type — raised by Spring MVC itself. */
    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiErrorResponse> handleSpringMvcError(Exception ex, HttpServletRequest request) {
        int status = (ex instanceof ErrorResponse errorResponse)
                ? errorResponse.getStatusCode().value()
                : ErrorCode.BAD_REQUEST.status().value();
        ErrorCode code = ErrorCode.fromStatus(status);

        log.warn("{} {} -> {}", request.getMethod(), request.getRequestURI(), code);
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, messageFor(code), request.getRequestURI()));
    }

    /** Last resort. Anything here is a bug, so it is logged in full and reported generically. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR, GENERIC_MESSAGE, request.getRequestURI()));
    }

    private static String messageFor(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> "That resource does not exist.";
            case METHOD_NOT_ALLOWED -> "That action is not supported on this resource.";
            case UNSUPPORTED_MEDIA_TYPE -> "That content type is not supported.";
            default -> "The request could not be completed.";
        };
    }

    private void logByStatus(ErrorCode code, HttpServletRequest request, ApiException ex) {
        if (code.status().is5xxServerError()) {
            log.error("{} on {} {}", code, request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.warn("{} on {} {}: {}", code, request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
    }

    private static String messageOf(String defaultMessage) {
        return defaultMessage == null ? "is invalid" : defaultMessage;
    }

    /** Turns {@code createResume.file.name} into {@code name} for a usable field label. */
    private static String lastNodeOf(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }
}
