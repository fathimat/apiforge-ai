package ai.apiforge;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded fixed-window limiter. Never trusts visitor-supplied forwarding headers. */
public final class RequestRateLimiter {
    private static final int MAX_PEERS = 2048;
    private final RequestLimits limits;
    private final Clock clock;
    private final Map<String, Integer> peers = new HashMap<>();
    private long window;
    private int total;

    public RequestRateLimiter(RequestLimits limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
        this.window = Math.floorDiv(clock.millis(), 60_000);
    }

    /** Returns zero when allowed, otherwise the Retry-After delay in seconds. */
    public synchronized int retryAfter(String peer) {
        long now = clock.millis();
        long currentWindow = Math.floorDiv(now, 60_000);
        if (window != currentWindow) {
            peers.clear();
            total = 0;
            window = currentWindow;
        }
        int count = peers.getOrDefault(peer, 0);
        if (total >= limits.globalPerMinute() || count >= limits.peerPerMinute()
                || (!peers.containsKey(peer) && peers.size() >= MAX_PEERS)) {
            return (int) (60 - Math.floorMod(now / 1000, 60));
        }
        total++;
        peers.put(peer, count + 1);
        return 0;
    }
}
