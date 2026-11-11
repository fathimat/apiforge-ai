package ai.apiforge;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
@Component public class JsonValidator {
 private final ObjectMapper mapper;
 public JsonValidator(ObjectMapper mapper) { this.mapper=mapper; }
 public Models.Validation validate(String text) {
 if(text.isBlank()) return new Models.Validation(true,"Empty body (valid for bodyless exchanges)","");
 try { JsonNode n=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
 return new Models.Validation(true,"Valid JSON",mapper.writerWithDefaultPrettyPrinter().writeValueAsString(n));
 } catch(Exception e) { return new Models.Validation(false,"Invalid JSON: check syntax, quoting, commas, and trailing content.",""); }
 }
}
