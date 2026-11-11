package ai.apiforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicSafetyFilterTest {
    final RequestLimits limits = new RequestLimits(1024, 4, 2, 1);
    final ObjectMapper mapper = new ObjectMapper();
    PublicSafetyFilter filter() {
        return new PublicSafetyFilter(limits, new RequestRateLimiter(limits, Clock.systemUTC()), mapper);
    }
    MockHttpServletRequest request() {
        var req = new MockHttpServletRequest("POST", "/api/validate");
        req.setRemoteAddr("192.0.2.1");
        req.setContentType("application/json");
        req.setContent("{}".getBytes());
        return req;
    }
    @Test void chunkedBodiesCannotBypassByteCap() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/validate") {
            @Override public long getContentLengthLong() { return -1; }
        };
        req.setContent(new byte[1025]);
        var response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        filter().doFilter(req, response, chain);
        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);
    }
    @Test void forwardedHeadersCannotResetPeerLimit() throws Exception {
        var filter = filter();
        for (int i = 0; i < 3; i++) {
            var req = request();
            req.addHeader("X-Forwarded-For", "198.51.100." + i);
            req.addHeader("Forwarded", "for=198.51.100." + i);
            var response = new MockHttpServletResponse();
            filter.doFilter(req, response, (a, b) -> {});
            assertEquals(i < 2 ? 200 : 429, response.getStatus());
            if (i == 2) assertNotNull(response.getHeader("Retry-After"));
        }
    }
    @Test void concurrencyIsBoundedAndPermitsAreReleased() throws Exception {
        var filter = filter();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {
            try {filter.doFilter(request(), new MockHttpServletResponse(), (a, b) -> {
                entered.countDown();
                try {if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out");}
                catch (InterruptedException e) {Thread.currentThread().interrupt();}
            });} catch (Throwable e) {failure.set(e);}
        });
        worker.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var response = new MockHttpServletResponse();
            filter.doFilter(request(), response, (a, b) -> fail("Concurrency cap bypassed"));
            assertEquals(503, response.getStatus());
        } finally {release.countDown(); worker.join(5000);}
        assertNull(failure.get());
        var next = request(); next.setRemoteAddr("192.0.2.3");
        var response = new MockHttpServletResponse();
        filter.doFilter(next, response, (a, b) -> {});
        assertEquals(200, response.getStatus());
    }
    @Test void globalLimitAndWindowResetWork() {
        var now = new AtomicReference<>(Instant.parse("2026-11-11T00:00:00Z"));
        var clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        var limiter = new RequestRateLimiter(limits, clock);
        for (int i=0;i<4;i++) assertEquals(0, limiter.retryAfter("peer-"+i));
        assertEquals(60, limiter.retryAfter("another-peer"));
        now.set(now.get().plusSeconds(60));
        assertEquals(0, limiter.retryAfter("another-peer"));
    }
    @Test void unexpectedFailuresDoNotLeakSecrets() throws Exception {
        var errors = new Errors();
        String body = mapper.writeValueAsString(errors.unexpected(new IllegalStateException("secret-password")).getBody());
        assertFalse(body.contains("secret-password"));
        assertFalse(body.contains("stackTrace"));
        assertTrue(body.contains("reference"));
    }
    @Test void byteLimitCountsUtf8BytesRatherThanCharacters() throws Exception {
        var req = request();
        req.setContent("é".repeat(600).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        filter().doFilter(req, response, (a, b) -> fail("UTF-8 byte cap bypassed"));
        assertEquals(413, response.getStatus());
    }
}
