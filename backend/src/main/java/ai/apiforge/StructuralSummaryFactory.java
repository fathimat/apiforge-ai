package ai.apiforge;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;

/** Provider data contains only enums, bounded counts, and flags: never arbitrary input strings. */
@Component
public class StructuralSummaryFactory {
    public enum Method { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }
    public enum Kind { EMPTY, INVALID, OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }
    public record Shape(Kind kind, int fields, int elements, int strings, int numbers,
                        int booleans, int nulls, int depth, boolean truncated) {}
    public record Summary(Method method, int status, Shape request, Shape response) {}
    private static final int MAX_NODES = 256;
    private static final int MAX_DEPTH = 8;
    private final ObjectMapper parser;

    public StructuralSummaryFactory(ObjectMapper mapper) {
        parser = mapper.copy();
        parser.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(50_000).maxNumberLength(1000)
                .maxDocumentLength(50_000).build());
    }

    public Summary summarize(Models.Exchange input) {
        if (input.status() < 100 || input.status() > 599) throw new IllegalArgumentException("Invalid HTTP status");
        return new Summary(Method.valueOf(input.method()), input.status(),
                summarizeBody(input.requestBody()), summarizeBody(input.responseBody()));
    }

    private Shape summarizeBody(String body) {
        if (body == null || body.length() > 50_000) throw new IllegalArgumentException("Invalid body length");
        if (body.isBlank()) return empty(Kind.EMPTY);
        JsonNode root;
        try {
            root = parser.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
        } catch (Exception ignored) {
            // Parsing exceptions may contain pasted secrets. Neither return nor log them.
            return empty(Kind.INVALID);
        }
        Stats stats = new Stats();
        visit(root, 0, stats);
        return new Shape(kind(root), stats.fields, stats.elements, stats.strings, stats.numbers,
                stats.booleans, stats.nulls, stats.depth, stats.truncated);
    }

    private static Shape empty(Kind kind) { return new Shape(kind, 0, 0, 0, 0, 0, 0, 0, false); }
    private static Kind kind(JsonNode node) {
        if (node.isObject()) return Kind.OBJECT;
        if (node.isArray()) return Kind.ARRAY;
        if (node.isTextual()) return Kind.STRING;
        if (node.isNumber()) return Kind.NUMBER;
        if (node.isBoolean()) return Kind.BOOLEAN;
        return Kind.NULL;
    }
    private static void visit(JsonNode node, int depth, Stats stats) {
        if (stats.nodes >= MAX_NODES) { stats.truncated = true; return; }
        stats.nodes++;
        stats.depth = Math.max(stats.depth, depth);
        if (node.isTextual()) stats.strings++;
        else if (node.isNumber()) stats.numbers++;
        else if (node.isBoolean()) stats.booleans++;
        else if (node.isNull()) stats.nulls++;
        if (!node.isContainerNode()) return;
        if (node.size() > MAX_NODES) stats.truncated = true;
        if (node.isObject()) stats.fields = Math.min(MAX_NODES, stats.fields + node.size());
        else stats.elements = Math.min(MAX_NODES, stats.elements + node.size());
        if (depth >= MAX_DEPTH) { stats.truncated |= !node.isEmpty(); return; }
        // Iterate values, never field names. No textValue/asText/value extraction occurs here.
        var children = node.elements();
        while (children.hasNext()) {
            if (stats.nodes >= MAX_NODES) { stats.truncated = true; break; }
            visit(children.next(), depth + 1, stats);
        }
    }
    private static class Stats {
        int nodes, fields, elements, strings, numbers, booleans, nulls, depth;
        boolean truncated;
    }
}
