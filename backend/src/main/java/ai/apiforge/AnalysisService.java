package ai.apiforge;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
@Service public class AnalysisService {
 private final JsonValidator validator; private final GeminiClient gemini; private final AnalysisRepository repository; private final ObjectMapper mapper;
 public AnalysisService(JsonValidator v,GeminiClient g,AnalysisRepository r,ObjectMapper m) {validator=v;gemini=g;repository=r;mapper=m;}
 public Models.Result analyze(Models.Exchange input) {
 var req=validator.validate(input.requestBody()); var res=validator.validate(input.responseBody());
 var tips=new ArrayList<String>();
 if(!req.valid()) tips.add("Fix the request JSON syntax before retrying.");
 if(!res.valid()) tips.add("The response is not JSON. Check Content-Type and whether a proxy returned HTML or plain text.");
 if(input.status()==401 || input.status()==403) tips.add("Check token expiry, authentication scheme, permissions, and authorization scope.");
 else if(input.status()==404) tips.add("Check the route, API version, and resource identifier.");
 else if(input.status()==429) tips.add("Respect Retry-After and use exponential backoff with jitter.");
 else if(input.status()>=500) tips.add("Inspect server logs and upstream dependencies using a correlation ID.");
 else if(input.status()>=400) tips.add("Compare request fields and headers against the API contract.");
 else tips.add("Check the response schema and business values against the API contract.");
 tips.add("These are suggestions based on pasted data; no request has been executed.");
 String source="local";
 if(input.useAi()) {try {tips.add(gemini.analyze(input));source="gemini";} catch(IllegalStateException e) {tips.add("Gemini unavailable or disabled; showing local suggestions.");source="local-fallback";}}
 var scenarios=List.of(new Models.Scenario("Expected response","Send a documented valid "+input.method()+" request to "+input.url(),"Documented success status, content type, and schema"),
 new Models.Scenario("Invalid input","Send malformed JSON or omit a required parameter","Documented 4xx with a safe, useful error"),
 new Models.Scenario("Authorization","If protected, retry with missing, expired, and insufficient-scope credentials","401 or 403 without sensitive data"),
 new Models.Scenario("Boundary conditions","Test empty, maximum-length, and out-of-range values","Contract-defined results without server errors"));
 try {var value=new Models.Result(null,java.time.Instant.now().toString(),source,req,res,tips,scenarios);
 var entity=repository.save(new Analysis(mapper.writeValueAsString(value)));
 return new Models.Result(entity.id,entity.createdAt.toString(),source,req,res,tips,scenarios);
 } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("Could not serialize analysis");}
 }
 public List<Models.Result> history() {return repository.findTop20ByOrderByCreatedAtDesc().stream().map(a->{try {var v=mapper.readValue(a.result,Models.Result.class);return new Models.Result(a.id,a.createdAt.toString(),v.source(),v.requestValidation(),v.responseValidation(),v.suggestions(),v.scenarios());}catch(Exception e){throw new IllegalStateException("Could not read analysis");}}).toList();}
}
