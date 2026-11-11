package ai.apiforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** In-process mock transport only. Enabling the test policy cannot contact Google. */
@SpringBootTest(properties = {"gemini.enabled=true", "gemini.api-key=test-placeholder-not-real"})
@AutoConfigureMockMvc @ActiveProfiles("demo")
class GeminiFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AnalysisRepository repository;
    @MockitoBean GeminiTransport transport;
    @MockitoBean AiAllowance allowance;

    @Test void consentedMockAiAndInvalidOutputRemainStatelessWithSafeFallback() throws Exception {
        long before = repository.count();
        when(transport.available()).thenReturn(true);
        when(allowance.tryAcquire()).thenReturn(true);
        String advice = """
            {"suggestions":["Check the response schema"],"scenarios":[{"name":"Boundary input","steps":"Try an out of range value","expected":"A documented validation error"}]}
            """;
        byte[] response = mapper.writeValueAsBytes(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
                "content", Map.of("parts", List.of(Map.of("text", advice)))))));
        when(transport.exchange(any(), anyInt())).thenReturn(new GeminiTransport.Reply(200, response));
        // Serialize to avoid accidental JSON escaping errors in nested body strings.
        var input = new Models.Exchange("POST", "https://private.example/secret", "{\"password\":\"private-marker\"}", "{}", 422, true, true);
        mvc.perform(post("/api/analyses").contentType("application/json").content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("gemini"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.scenarios[0].name").value("Boundary input"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(transport, times(1)).exchange(any(), anyInt());
        when(transport.exchange(any(), anyInt())).thenReturn(new GeminiTransport.Reply(200, new byte[0]));
        mvc.perform(post("/api/analyses").contentType("application/json").content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("local-fallback"))
                .andExpect(jsonPath("$.scenarios.length()").value(4));
        verify(allowance, times(2)).release();
        assertEquals(before, repository.count());
        mvc.perform(get("/api/analyses")).andExpect(status().isGone());
    }
}
