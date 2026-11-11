package ai.apiforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ApiController.class, properties = {
    "app.limits.peer-per-minute=2", "app.limits.global-per-minute=4"
})
@Import({WebSafetyConfig.class, AnalysisService.class, JsonValidator.class})
class RateLimitIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void corsHeadersAndRetryAfterArePresentOnRateLimitedResponses() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/validate").header("Origin", "http://127.0.0.1:5173")
                    .header("X-Forwarded-For", "198.51.100." + i)
                    .contentType("application/json").content("{\"json\":\"{}\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/validate").header("Origin", "http://127.0.0.1:5173")
                .header("X-Forwarded-For", "198.51.100.250")
                .contentType("application/json").content("{\"json\":\"{}\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"))
                .andExpect(header().string("Cache-Control", "no-store"));
        // Provider liveness checks must not be made unhealthy by visitor quotas.
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
