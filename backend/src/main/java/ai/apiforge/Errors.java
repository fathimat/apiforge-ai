package ai.apiforge;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestControllerAdvice public class Errors {
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
 public ResponseEntity<ProblemDetail> badRequest(Exception e){return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid input. Check method, HTTP(S) URL, status (100–599), and body limits (50,000 characters)."));}
 @ExceptionHandler(Exception.class) public ResponseEntity<ProblemDetail> failure(Exception e){return ResponseEntity.status(500).body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,"Unable to process analysis. Check local service and database availability."));}
}
