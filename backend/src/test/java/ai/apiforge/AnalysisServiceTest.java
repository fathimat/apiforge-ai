package ai.apiforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {
    final GeminiClient gemini = mock(GeminiClient.class);
    final AnalysisService service = new AnalysisService(new JsonValidator(new ObjectMapper()), gemini);
    Models.Exchange input(boolean ai, boolean consent, int status) {
        return new Models.Exchange("GET", "https://example.com", "", "{}", status, ai, consent);
    }
    @Test void localAnalysisIsStatelessAndNeverTouchesAi() {
        var result = service.analyze(input(false, false, 401));
        assertEquals("local", result.source());
        assertNull(result.id());
        assertEquals(4, result.scenarios().size());
        assertTrue(result.suggestions().getFirst().contains("token"));
        verifyNoInteractions(gemini);
    }
    @Test void disabledAiFallsBackWithoutLosingLocalScenarios() {
        when(gemini.analyze(any())).thenReturn(new GeminiClient.Attempt(GeminiClient.Outcome.DISABLED, null));
        var result = service.analyze(input(true, true, 200));
        assertEquals("local-fallback", result.source());
        assertNull(result.id());
        assertEquals(4, result.scenarios().size());
        assertTrue(result.suggestions().getLast().contains("No data was sent"));
    }
    @Test void validAiAddsSuggestionsAndReplacesScenarioIdeasWithoutPersistence() {
        var ideas = List.of(new Models.Scenario("AI boundary case", "Check a missing field", "Safe validation error"));
        when(gemini.analyze(any())).thenReturn(new GeminiClient.Attempt(GeminiClient.Outcome.SUCCESS,
                new GeminiClient.Advice(List.of("AI troubleshooting suggestion"), ideas)));
        var result = service.analyze(input(true, true, 422));
        assertEquals("gemini", result.source());
        assertNull(result.id());
        assertTrue(result.suggestions().contains("AI troubleshooting suggestion"));
        assertEquals(ideas, result.scenarios());
    }
    @Test void everyFailurePreservesLocalAnalysis() {
        for (var outcome : GeminiClient.Outcome.values()) {
            if (outcome == GeminiClient.Outcome.SUCCESS) continue;
            when(gemini.analyze(any())).thenReturn(new GeminiClient.Attempt(outcome, null));
            var result = service.analyze(input(true, true, 500));
            assertEquals("local-fallback", result.source(), outcome.name());
            assertEquals(4, result.scenarios().size());
            assertTrue(result.suggestions().getFirst().contains("server logs"));
        }
    }
    @Test void detectsInvalidBodiesAndRateLimits() {
        var result = service.analyze(new Models.Exchange("POST", "https://example.com", "{", "<html>", 429, false));
        assertFalse(result.requestValidation().valid());
        assertFalse(result.responseValidation().valid());
        assertTrue(result.suggestions().toString().contains("Retry-After"));
        verifyNoInteractions(gemini);
    }
}
