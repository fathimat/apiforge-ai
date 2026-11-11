package ai.apiforge;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
class AnalysisServiceTest {
 final ObjectMapper mapper=new ObjectMapper(); final GeminiClient gemini=mock(GeminiClient.class);final AnalysisRepository repo=mock(AnalysisRepository.class);
 final AnalysisService service=new AnalysisService(new JsonValidator(mapper),gemini,repo,mapper);
 @BeforeEach void setup(){when(repo.save(any())).thenAnswer(i->{Analysis a=i.getArgument(0);a.id=1L;return a;});}
 Models.Exchange input(boolean ai,int status){return new Models.Exchange("GET","https://example.com","","{}",status,ai);}
 @Test void localModeNeverCallsGoogle(){var r=service.analyze(input(false,401));assertEquals("local",r.source());assertEquals(4,r.scenarios().size());assertTrue(r.suggestions().getFirst().contains("token"));verifyNoInteractions(gemini);verify(repo).save(any());}
 @Test void usesGeminiOnlyWhenRequested(){when(gemini.analyze(any())).thenReturn("Inspect validation rules");var r=service.analyze(input(true,422));assertEquals("gemini",r.source());assertTrue(r.suggestions().contains("Inspect validation rules"));}
 @Test void providerFailuresFallBack(){when(gemini.analyze(any())).thenThrow(new IllegalStateException("secret provider detail"));var r=service.analyze(input(true,500));assertEquals("local-fallback",r.source());assertFalse(r.suggestions().toString().contains("secret provider detail"));}
 @Test void detectsInvalidBodiesAndRateLimits(){var r=service.analyze(new Models.Exchange("POST","https://example.com","{","<html>",429,false));assertFalse(r.requestValidation().valid());assertFalse(r.responseValidation().valid());assertTrue(r.suggestions().toString().contains("Retry-After"));}
 @Test void disabledGeminiRejectsWithoutNetwork(){var client=new GeminiClient("","gemini-2.5-flash",false,mapper);assertThrows(IllegalStateException.class,()->client.analyze(input(true,200)));}
}
