package ai.apiforge;

import java.net.http.HttpRequest;
import org.springframework.stereotype.Component;

/** Hard stop for the permanent zero-budget preparation release, independent of environment flags. */
@Component
public final class DisabledGeminiTransport implements GeminiTransport {
    @Override public boolean available() { return false; }
    @Override public Reply exchange(HttpRequest request, int maxResponseBytes) {
        throw new IllegalStateException("Live Gemini transport is not installed");
    }
}
