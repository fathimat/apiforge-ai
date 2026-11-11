package ai.apiforge;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/api") public class ApiController {
 private final AnalysisService service; private final JsonValidator validator;
 public ApiController(AnalysisService s,JsonValidator v){service=s;validator=v;}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok","application","APIForge AI");}
 @PostMapping("/validate") public Models.Validation validate(@Valid @RequestBody Models.JsonInput input){return validator.validate(input.json());}
 @PostMapping("/analyses") public Models.Result analyze(@Valid @RequestBody Models.Exchange input){return service.analyze(input);}
 @GetMapping("/analyses") public List<Models.Result> history(){return service.history();}
}
