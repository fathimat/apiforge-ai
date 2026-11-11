package ai.apiforge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Stateless by design: anonymous visitors must never share stored results. */
@Service
public class AnalysisService {
    private final JsonValidator validator;
    private final GeminiClient gemini;

    public AnalysisService(JsonValidator validator, GeminiClient gemini) {
        this.validator = validator;
        this.gemini = gemini;
    }

    public Models.Result analyze(Models.Exchange input) {
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
        String source = "local";
        if (input.useAi()) {
            var attempt = gemini.analyze(input);
            if (attempt.outcome() == GeminiClient.Outcome.SUCCESS) {
                tips.addAll(attempt.advice().suggestions());
                scenarios = attempt.advice().scenarios();
                source = "gemini";
            } else {
                source = "local-fallback";
                tips.add(switch (attempt.outcome()) {
                    case CONSENT_REQUIRED -> "No AI request was sent: explicit visitor consent is required. Local analysis is shown.";
                    case DISABLED -> "Gemini is disabled. No data was sent to Google; local analysis is shown.";
                    case LIMITED -> "The free AI allowance is unavailable. Local analysis is shown.";
                    case INVALID_SUMMARY -> "A safe structural summary could not be prepared. Local analysis is shown.";
                    default -> "AI advice is unavailable or could not be validated. Local analysis is shown.";
                });
            }
        }
        // No repository access, body storage, cookies, session IDs, or analysis IDs.
        return new Models.Result(null, Instant.now().toString(), source, request, response, tips, scenarios);
    }
}
