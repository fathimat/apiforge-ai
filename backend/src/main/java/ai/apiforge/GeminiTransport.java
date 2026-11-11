package ai.apiforge;

import java.io.IOException;
import java.net.http.HttpRequest;

/** Test seam. This release has NO live implementation and no HTTP client bean. */
public interface GeminiTransport {
    record Reply(int status, byte[] body) {
        public Reply { body = body.clone(); }
        @Override public byte[] body() { return body.clone(); }
    }
    boolean available();
    /** A future approved transport must bound bytes while reading, honor request.timeout(),
     * connect within five seconds, reject redirects, and never retry or log request headers. */
    Reply exchange(HttpRequest request, int maxResponseBytes) throws IOException, InterruptedException;
}
