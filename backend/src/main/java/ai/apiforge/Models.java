package ai.apiforge;
import jakarta.validation.constraints.*;
import java.util.List;
public class Models {
 public record Exchange(@NotBlank @Pattern(regexp="GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method,
 @NotBlank @Size(max=2048) @Pattern(regexp="https?://[^\\s]+") String url,
 @NotNull @Size(max=50000) String requestBody, @NotNull @Size(max=50000) String responseBody,
 @Min(100) @Max(599) int status, boolean useAi, boolean aiConsent) {
 public Exchange(String method, String url, String requestBody, String responseBody, int status, boolean useAi) {
  this(method, url, requestBody, responseBody, status, useAi, false);
 }
 }
 public record JsonInput(@NotNull @Size(max=50000) String json) {}
 public record Validation(boolean valid,String message,String formatted) {}
 public record Scenario(String name,String steps,String expected) {}
 public record Result(Long id,String createdAt,String source,Validation requestValidation,Validation responseValidation,List<String> suggestions,List<Scenario> scenarios) {}
}
