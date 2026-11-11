package ai.apiforge;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AnalysisService service;
    private final JsonValidator validator;

    public ApiController(AnalysisService service, JsonValidator validator) {
        this.service = service;
        this.validator = validator;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "application", "APIForge AI");
    }

    @PostMapping(value = "/validate", consumes = "application/json")
    public Models.Validation validate(@Valid @RequestBody Models.JsonInput input) {
        return validator.validate(input.json());
    }

    @PostMapping(value = "/analyses", consumes = "application/json")
    public Models.Result analyze(@Valid @RequestBody Models.Exchange input) {
        return service.analyze(input);
    }

    @GetMapping("/analyses")
    public void history() {
        throw new ResponseStatusException(HttpStatus.GONE, "History is disabled for this demo.");
    }
}
