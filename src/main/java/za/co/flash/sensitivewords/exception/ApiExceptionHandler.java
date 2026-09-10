package za.co.flash.sensitivewords.exception;

import java.sql.SQLException;
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
        return super.handleExceptionInternal(exception, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
    }
}
