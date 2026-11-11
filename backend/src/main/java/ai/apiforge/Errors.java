package ai.apiforge;

import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class Errors extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(Errors.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Do not echo rejected values, exception messages, URLs, SQL, or provider responses.
        String detail = switch (status.value()) {
            case 400 -> "Invalid input. Check the method, HTTP(S) URL, status, JSON envelope, and 50,000-character body limits.";
            case 404 -> "Resource not found.";
            case 405 -> "Method not allowed.";
            case 410 -> "History is disabled. This demo does not save analysis results.";
            case 415 -> "Use an application/json request body.";
            default -> "The request could not be processed.";
        };
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create("/api"));
        return new ResponseEntity<>(problem, headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception error) {
        String reference = UUID.randomUUID().toString();
        // Exception text and stack traces can contain request or database credentials.
        LOG.error("Request failed; reference={}, type={}", reference, error.getClass().getSimpleName());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to process the request. Please try again later.");
        problem.setInstance(URI.create("/api"));
        problem.setProperty("reference", reference);
        return ResponseEntity.internalServerError().body(problem);
    }
}
