package ai.apiforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("demo")
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AnalysisRepository repository;

    @Test void analyzesWithoutPersistingOrExposingHistory() throws Exception {
        long before = repository.count();
        mvc.perform(post("/api/analyses").contentType("application/json").content("""
            {"method":"POST","url":"https://example.com/private-marker","requestBody":"{}","responseBody":"{}","status":422,"useAi":false}
            """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.source").value("local"))
                .andExpect(jsonPath("$.scenarios.length()").value(4))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/analyses").with(req -> {req.setRemoteAddr("192.0.2.2"); return req;}))
                .andExpect(status().isGone()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-marker"))));
        assertEquals(before, repository.count());
    }
    @Test void validatesAndRejectsInvalidEnvelopes() throws Exception {
        mvc.perform(post("/api/validate").contentType("application/json").content("{\"json\":\"{} {}\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
        mvc.perform(post("/api/analyses").contentType("application/json").content("""
            {"method":"TRACE","url":"file:///etc/passwd","requestBody":"","responseBody":"","status":999}
            """))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/etc/passwd"))));
    }
    @Test void rejectsNullOversizedAndDeepJson() throws Exception {
        mvc.perform(post("/api/validate").contentType("application/json").content("{\"json\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/validate").contentType("application/json").content("{\"json\":\"" + "a".repeat(50001) + "\"}"))
                .andExpect(status().isBadRequest());
        String nested = "[".repeat(65) + "0" + "]".repeat(65);
        mvc.perform(post("/api/validate").contentType("application/json").content("{\"json\":\"" + nested + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
    }
    @Test void enforcesByteLimitContentTypeAndSafeErrors() throws Exception {
        mvc.perform(post("/api/validate").contentType("application/json").content("x".repeat(131073)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/validate").contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/validate").contentType("application/json").header("Content-Encoding", "gzip").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/validate").contentType("application/json").content("sensitive-marker"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-marker"))));
    }
    @Test void disabledAiFallsBackWithoutSavingResults() throws Exception {
        mvc.perform(post("/api/analyses").contentType("application/json").content("""
            {"method":"GET","url":"https://example.com","requestBody":"","responseBody":"","status":200,"useAi":true,"aiConsent":true}
            """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("local-fallback"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.suggestions").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("No data was sent"))));
        assertEquals(0, repository.count());
    }
    @Test void corsOnlyAllowsConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/analyses").header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/analyses").header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(post("/api/analyses").header("Origin", "https://attacker.example").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test void healthHasSafetyHeaders() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
