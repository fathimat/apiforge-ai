package ai.apiforge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Stateless by design: anonymous visitors must never share stored results. */
@Service
public class AnalysisService {
    private final JsonValidator validator;

    public AnalysisService(JsonValidator validator) {
        this.validator = validator;
    }

    public Models.Result analyze(Models.Exchange input) {
        // Deliberate server-side lock. An environment variable cannot enable AI in this demo.
        if (input.useAi()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gemini is disabled for this demo.");
        }
        var request = validator.validate(input.requestBody());
        var response = validator.validate(input.responseBody());
        var tips = new ArrayList<String>();
        if (!request.valid()) tips.add("Fix the request JSON syntax before retrying.");
        if (!response.valid()) tips.add("The response is not JSON. Check Content-Type and whether a proxy returned HTML or plain text.");
        switch (input.status()) {
            case 401, 403 -> tips.add("Check token expiry, authentication scheme, permissions, and authorization scope.");
            case 404 -> tips.add("Check the route, API version, and resource identifier.");
            case 429 -> tips.add("Respect Retry-After and use exponential backoff with jitter.");
            default -> {
                if (input.status() >= 500) tips.add("Inspect server logs and upstream dependencies using a correlation ID.");
                else if (input.status() >= 400) tips.add("Compare request fields and headers against the API contract.");
                else tips.add("Check the response schema and business values against the API contract.");
            }
        }
        tips.add("These are suggestions based on pasted data; no request has been executed.");
        var scenarios = List.of(
                new Models.Scenario("Expected response", "Send a documented valid " + input.method() + " request to " + input.url(), "Documented success status, content type, and schema"),
                new Models.Scenario("Invalid input", "Send malformed JSON or omit a required parameter", "Documented 4xx with a safe, useful error"),
                new Models.Scenario("Authorization", "If protected, retry with missing, expired, and insufficient-scope credentials", "401 or 403 without sensitive data"),
                new Models.Scenario("Boundary conditions", "Test empty, maximum-length, and out-of-range values", "Contract-defined results without server errors"));
        // No repository access, body storage, cookies, session IDs, or analysis IDs.
        return new Models.Result(null, Instant.now().toString(), "local", request, response, tips, scenarios);
    }
}
