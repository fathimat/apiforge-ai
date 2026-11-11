package ai.apiforge;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiClientTest {
    static final String KEY = "unit-test-placeholder-key-not-real";
    static final String GOOD = """
        {"suggestions":["Check the documented schema"],"scenarios":[{"name":"Missing field","steps":"Omit a required field","expected":"Documented validation error"}]}
        """;
    final ObjectMapper mapper = new ObjectMapper();
    final GeminiTransport transport = mock(GeminiTransport.class);
    GeminiClient client;
    @BeforeEach void setup() {
        when(transport.available()).thenReturn(true);
        client = create(true, KEY, GeminiClient.REVIEWED_FREE_MODEL, transport);
    }
    GeminiClient create(boolean enabled, String key, String model, GeminiTransport wire) {
        return new GeminiClient(mapper, new StructuralSummaryFactory(mapper), wire, new AiAllowance(), enabled, key, model);
    }
    Models.Exchange input(boolean consent) {
        return new Models.Exchange("POST", "https://private.example?key=endpoint-secret", "{\"private-field\":\"body-secret\"}", "{\"error\":\"response-secret\"}", 422, true, consent);
    }
    byte[] envelope(String advice, String finish) throws Exception {
        return mapper.writeValueAsBytes(Map.of("candidates", List.of(Map.of("finishReason", finish,
                "content", Map.of("parts", List.of(Map.of("text", advice)))))));
    }
    void reply(int status, byte[] body) throws Exception {
        when(transport.exchange(any(), eq(GeminiClient.MAX_RESPONSE_BYTES))).thenReturn(new GeminiTransport.Reply(status, body));
    }
    @Test void successfulMockResponseReturnsTypedAdviceAndOnlySanitizedDataIsSent() throws Exception {
        reply(200, envelope(GOOD, "STOP"));
        var result = client.analyze(input(true));
        assertEquals(GeminiClient.Outcome.SUCCESS, result.outcome());
        assertEquals("Missing field", result.advice().scenarios().getFirst().name());
        var capture = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).exchange(capture.capture(), eq(65536));
        HttpRequest request = capture.getValue();
        assertEquals("generativelanguage.googleapis.com", request.uri().getHost());
        assertTrue(request.uri().getPath().contains(GeminiClient.REVIEWED_FREE_MODEL));
        assertFalse(request.uri().toString().contains(KEY));
        assertEquals(KEY, request.headers().firstValue("x-goog-api-key").orElseThrow());
        assertEquals(Duration.ofSeconds(20), request.timeout().orElseThrow());
        JsonNode payload = mapper.readTree(readBody(request));
        assertFalse(payload.has("tools"));
        assertEquals(1024, payload.path("generationConfig").path("maxOutputTokens").asInt());
        assertEquals("application/json", payload.path("generationConfig").path("responseMimeType").asText());
        String summary = payload.path("contents").path(0).path("parts").path(0).path("text").asText();
        for (String marker : new String[]{"private", "secret", "error", "example", KEY}) assertFalse(summary.contains(marker));
        assertTrue(payload.path("systemInstruction").toString().contains("untrusted"));
    }
    @Test void absentConsentMakesZeroProviderCalls() throws Exception {
        assertEquals(GeminiClient.Outcome.CONSENT_REQUIRED, client.analyze(input(false)).outcome());
        verify(transport, never()).exchange(any(), anyInt());
    }
    @Test void disabledMissingKeyOrUnreviewedModelMakesZeroProviderCalls() throws Exception {
        for (GeminiClient blocked : List.of(create(false, KEY, GeminiClient.REVIEWED_FREE_MODEL, transport),
                create(true, "", GeminiClient.REVIEWED_FREE_MODEL, transport), create(true, KEY, "paid-or-unreviewed-model", transport),
                create(true, KEY, "gemini-2.5-flash", transport))) {
            assertEquals(GeminiClient.Outcome.DISABLED, blocked.analyze(input(true)).outcome());
        }
        verify(transport, never()).exchange(any(), anyInt());
    }
    @Test void bundledTransportCannotBeEnabledByEnvironmentFlags() {
        var disabled = new DisabledGeminiTransport();
        assertFalse(disabled.available());
        assertEquals(GeminiClient.Outcome.DISABLED, create(true, KEY, GeminiClient.REVIEWED_FREE_MODEL, disabled).analyze(input(true)).outcome());
        assertThrows(IllegalStateException.class, () -> disabled.exchange(HttpRequest.newBuilder(java.net.URI.create("https://example.invalid")).build(), 10));
    }
    @Test void providerHttpFailuresFallBackWithoutRetriesOrBodyDisclosure() throws Exception {
        for (int status : new int[]{400, 401, 403, 429, 500, 503}) {
            reset(transport); when(transport.available()).thenReturn(true);
            reply(status, "provider-secret-error".getBytes(StandardCharsets.UTF_8));
            var current = create(true, KEY, GeminiClient.REVIEWED_FREE_MODEL, transport);
            var result = current.analyze(input(true));
            assertEquals(GeminiClient.Outcome.UNAVAILABLE, result.outcome());
            assertNull(result.advice());
            assertEquals(GeminiClient.Outcome.LIMITED, current.analyze(input(true)).outcome());
            verify(transport, times(1)).exchange(any(), anyInt());
        }
    }
    @Test void timeoutsAndConnectionFailuresNeverRetryOrExposeMessages() throws Exception {
        for (IOException error : new IOException[]{new HttpTimeoutException("secret-timeout"),
                new HttpConnectTimeoutException("secret-connect"), new IOException("secret-connection")}) {
            reset(transport); when(transport.available()).thenReturn(true);
            when(transport.exchange(any(), anyInt())).thenThrow(error);
            var current = create(true, KEY, GeminiClient.REVIEWED_FREE_MODEL, transport);
            assertEquals(GeminiClient.Outcome.UNAVAILABLE, current.analyze(input(true)).outcome());
            verify(transport, times(1)).exchange(any(), anyInt());
        }
    }
    @Test void interruptionIsPreservedAndPermitIsReleased() throws Exception {
        when(transport.exchange(any(), anyInt())).thenThrow(new InterruptedException("secret-interruption"));
        try {
            assertEquals(GeminiClient.Outcome.UNAVAILABLE, client.analyze(input(true)).outcome());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {Thread.interrupted();}
    }
    @Test void malformedEmptyTruncatedBlockedAndOversizedEnvelopesAreRejected() throws Exception {
        List<byte[]> cases = List.of("not-json".getBytes(StandardCharsets.UTF_8), new byte[0], new byte[65537],
                "{}".getBytes(StandardCharsets.UTF_8), envelope(GOOD, "MAX_TOKENS"), envelope(GOOD, "SAFETY"),
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}".getBytes(StandardCharsets.UTF_8));
        for (byte[] body : cases) {
            reply(200, body);
            assertEquals(GeminiClient.Outcome.INVALID_OUTPUT,
                    create(true, KEY, GeminiClient.REVIEWED_FREE_MODEL, transport).analyze(input(true)).outcome());
        }
    }
    @Test void wrongSchemaUnknownDuplicateFieldsAndExcessiveTextAreRejected() throws Exception {
        List<String> cases = List.of("{}", "[]", "null", GOOD+" {}", GOOD.replace("[\"Check the documented schema\"]", "[]"),
                GOOD.replace("\"Check the documented schema\"", "123"), GOOD.replace("Check the documented schema", "a".repeat(501)),
                GOOD.replace("\"suggestions\":", "\"extra\":1,\"suggestions\":"), GOOD.replace("\"suggestions\":", "\"suggestions\":[],\"suggestions\":"),
                GOOD.replace("\"expected\":", "\"execute\":true,\"expected\":"), GOOD.replace("\"name\":\"Missing field\",", ""));
        for (String body : cases) {
            reply(200, envelope(body, "STOP"));
            assertEquals(GeminiClient.Outcome.INVALID_OUTPUT,
                    create(true, KEY, GeminiClient.REVIEWED_FREE_MODEL, transport).analyze(input(true)).outcome(), body);
        }
    }
    @Test void linksHtmlCredentialPatternsAndHeaderKeyEchoesAreRejected() throws Exception {
        for (String unsafe : new String[]{"https://attacker.example", "<script>alert(1)</script>", "Bearer abcdefghijklmnopqrstuvwxyz", KEY,
                "-----BEGIN PRIVATE KEY-----", "line one\nline two", "```shell"}) {
            var body = mapper.readTree(GOOD);
            ((com.fasterxml.jackson.databind.node.ArrayNode) body.path("suggestions")).set(0, mapper.getNodeFactory().textNode(unsafe));
            reply(200, envelope(mapper.writeValueAsString(body), "STOP"));
            assertEquals(GeminiClient.Outcome.INVALID_OUTPUT,
                    create(true, KEY, GeminiClient.REVIEWED_FREE_MODEL, transport).analyze(input(true)).outcome(), unsafe);
        }
    }
    @Test void aiConcurrencyIsOneAndOtherRequestsFallBackWithoutWaiting() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(transport.exchange(any(), anyInt())).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
            return new GeminiTransport.Reply(200, envelope(GOOD, "STOP"));
        });
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {try {assertEquals(GeminiClient.Outcome.SUCCESS, client.analyze(input(true)).outcome());} catch(Throwable error) {failure.set(error);}});
        worker.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(GeminiClient.Outcome.LIMITED, client.analyze(input(true)).outcome());
        } finally {release.countDown(); worker.join(5000);}
        assertNull(failure.get()); verify(transport, times(1)).exchange(any(), anyInt());
    }
    private String readBody(HttpRequest request) throws Exception {
        var body = new java.io.ByteArrayOutputStream();
        var done = new CompletableFuture<Void>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) {subscription.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer buffer) {byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); body.writeBytes(bytes);}
            public void onError(Throwable error) {done.completeExceptionally(error);}
            public void onComplete() {done.complete(null);}
        });
        done.get(2, TimeUnit.SECONDS);
        return body.toString(StandardCharsets.UTF_8);
    }
}
