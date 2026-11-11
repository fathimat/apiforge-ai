package ai.apiforge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
class JsonValidatorTest {
 final JsonValidator validator=new JsonValidator(new ObjectMapper());
 @Test void acceptsJsonValuesAndEmptyBody(){for(String s:new String[]{"", "{}", "[]", "null", "true", "42", "\"text\""}) assertTrue(validator.validate(s).valid(),s);}
 @Test void rejectsMalformedAndTrailingContent(){for(String s:new String[]{"{", "{\"a\":}", "{} {}", "true garbage"}) assertFalse(validator.validate(s).valid(),s);}
 @Test void formatsValidJson(){assertTrue(validator.validate("{\"a\":1}").formatted().contains("\"a\" : 1"));}
}
