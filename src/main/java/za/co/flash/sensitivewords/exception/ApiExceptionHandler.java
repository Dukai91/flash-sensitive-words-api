package za.co.flash.sensitivewords.exception;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({TransientDataAccessException.class, DataAccessResourceFailureException.class, TransactionException.class})
    ProblemDetail dependency(Exception exception) {
        log.warn("Database request failed; failure={}", exception.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Database operation unavailable; a write may have committed. Read the resource before retrying");
    }

    @ExceptionHandler(InvalidInputException.class)
    ProblemDetail invalid(InvalidInputException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(WordNotFoundException.class)
    ProblemDetail notFound(WordNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DuplicateWordException.class)
    ProblemDetail duplicate(DuplicateWordException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MatcherUnavailableException.class)
    ProblemDetail unavailable(MatcherUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException exception) {
        // SQL Server uniqueness violations, including races across application instances.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627)) {
                return duplicate(new DuplicateWordException());
            }
        }
        return unexpected(exception);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Request failed unexpectedly", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = switch (status.value()) {
            case 400 -> "Invalid request. Check the JSON body, field constraints and parameters";
            case 404 -> "The requested resource was not found";
            case 405 -> "This HTTP method is not supported for the resource";
            case 406 -> "The requested response content type is not supported";
            case 415 -> "The request content type must be application/json";
            default -> "The request could not be processed";
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (exception instanceof MethodArgumentNotValidException validation) {
            problem.setDetail("Request fields failed validation");
            problem.setProperty("violations", validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> Map.of("field", error.getField(), "message",
                            java.util.Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"))).toList());
        } else if (exception instanceof HandlerMethodValidationException validation) {
            problem.setDetail("Request parameters failed validation");
            problem.setProperty("violations", validation.getParameterValidationResults().stream()
                    .flatMap(result -> result.getResolvableErrors().stream().map(error -> Map.of(
                            "field", java.util.Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "parameter"),
                            "message", java.util.Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")))).toList());
        } else if (exception instanceof HttpMessageNotReadableException) {
            problem.setDetail("Body must be one JSON object with unique properties and string field values");
        }
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }
}
