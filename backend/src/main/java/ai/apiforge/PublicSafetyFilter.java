package ai.apiforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import org.springframework.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies before MVC/deserialization; bounds actual bytes even with chunked transfer. */
public final class PublicSafetyFilter extends OncePerRequestFilter {
    private final RequestLimits limits;
    private final RequestRateLimiter limiter;
    private final ObjectMapper mapper;
    private final Semaphore inFlight;

    public PublicSafetyFilter(RequestLimits limits, RequestRateLimiter limiter, ObjectMapper mapper) {
        this.limits = limits;
        this.limiter = limiter;
        this.mapper = mapper;
        this.inFlight = new Semaphore(limits.maxConcurrent());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Strict-Transport-Security", "max-age=31536000");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
        if (request.getRequestURI().equals("/api/health") && request.getMethod().equals("GET")) {
            chain.doFilter(request, response);
            return;
        }
        int retry = limiter.retryAfter(request.getRemoteAddr());
        if (retry > 0) {
            response.setHeader("Retry-After", Integer.toString(retry));
            reject(response, 429, "Too many requests. Please wait and retry.");
            return;
        }
        if (!inFlight.tryAcquire()) {
            response.setHeader("Retry-After", "2");
            reject(response, 503, "The demo is busy. Please retry shortly.");
            return;
        }
        try {
            String encoding = request.getHeader("Content-Encoding");
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
                reject(response, 415, "Compressed request bodies are not accepted.");
                return;
            }
            if (request.getContentLengthLong() > limits.maxBodyBytes()) {
                reject(response, 413, "Request body exceeds the byte limit.");
                return;
            }
            // Do not rely on Content-Length: chunked bodies and dishonest lengths are bounded too.
            byte[] body = request.getInputStream().readNBytes(limits.maxBodyBytes() + 1);
            if (body.length > limits.maxBodyBytes()) {
                reject(response, 413, "Request body exceeds the byte limit.");
                return;
            }
            chain.doFilter(new BufferedRequest(request, body), response);
        } finally {
            inFlight.release();
        }
    }

    private void reject(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setInstance(URI.create("/api"));
        mapper.writeValue(response.getOutputStream(), problem);
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            var stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return stream.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return stream.read(bytes, offset, length); }
                @Override public boolean isFinished() { return stream.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous requests only"); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }
}
