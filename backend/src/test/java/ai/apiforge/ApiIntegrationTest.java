package ai.apiforge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("demo") class ApiIntegrationTest {
 @Autowired MockMvc mvc;
 @Test void validatesAndPersistsExchange() throws Exception {
 mvc.perform(post("/api/validate").contentType("application/json").content("{\"json\":\"{} {}\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
 mvc.perform(post("/api/analyses").contentType("application/json").content("""
 {"method":"POST","url":"https://example.com","requestBody":"{}","responseBody":"{}","status":422,"useAi":false}
 """)).andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.source").value("local")).andExpect(jsonPath("$.scenarios.length()").value(4));
 mvc.perform(get("/api/analyses")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").isNumber());
 }
 @Test void rejectsInvalidExchange() throws Exception {mvc.perform(post("/api/analyses").contentType("application/json").content("""
 {"method":"TRACE","url":"file:///etc/passwd","requestBody":"","responseBody":"","status":999}
 """)).andExpect(status().isBadRequest());}
 @Test void rejectsNullAndOversizedBodies() throws Exception {
 mvc.perform(post("/api/validate").contentType("application/json").content("{\"json\":null}")).andExpect(status().isBadRequest());
 mvc.perform(post("/api/validate").contentType("application/json").content("{\"json\":\""+"a".repeat(50001)+"\"}")).andExpect(status().isBadRequest());
 }
}
