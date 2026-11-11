package ai.apiforge;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceTest {
    final AnalysisService service = new AnalysisService(new JsonValidator(new ObjectMapper()));
    Models.Exchange input(boolean ai, int status) {
        return new Models.Exchange("GET", "https://example.com", "", "{}", status, ai);
    }
    @Test void localAnalysisIsStateless() {
        var result = service.analyze(input(false, 401));
        assertEquals("local", result.source());
        assertNull(result.id());
        assertEquals(4, result.scenarios().size());
        assertTrue(result.suggestions().getFirst().contains("token"));
    }
    @Test void aiIsRejectedBeforeAnyAnalysis() {
        var error = assertThrows(ResponseStatusException.class, () -> service.analyze(input(true, 200)));
        assertEquals(400, error.getStatusCode().value());
    }
    @Test void detectsInvalidBodiesAndRateLimits() {
        var result = service.analyze(new Models.Exchange("POST", "https://example.com", "{", "<html>", 429, false));
        assertFalse(result.requestValidation().valid());
        assertFalse(result.responseValidation().valid());
        assertTrue(result.suggestions().toString().contains("Retry-After"));
    }
    @Test void disabledGeminiRejectsWithoutNetwork() {
        var client = new GeminiClient("", "gemini-2.5-flash", false, new ObjectMapper());
        assertThrows(IllegalStateException.class, () -> client.analyze(input(true, 200)));
    }
}
