package ai.apiforge;

import java.time.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/** Aggregate process-local abuse limits; not a billing control or durable daily spending cap. */
@Component
public class AiAllowance {
    private final Clock clock;
    private final Semaphore concurrency = new Semaphore(1);
    private long minute = Long.MIN_VALUE;
    private LocalDate day;
    private int minuteCount, dayCount;
    private Instant cooldownUntil = Instant.MIN;
    public AiAllowance() { this(Clock.systemUTC()); }
    AiAllowance(Clock clock) { this.clock = clock; }

    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (now.isBefore(cooldownUntil)) return false;
        long currentMinute = Math.floorDiv(now.toEpochMilli(), 60_000);
        LocalDate currentDay = now.atZone(ZoneOffset.UTC).toLocalDate();
        if (currentMinute != minute) { minute = currentMinute; minuteCount = 0; }
        if (!currentDay.equals(day)) { day = currentDay; dayCount = 0; }
        if (minuteCount >= 2 || dayCount >= 20 || !concurrency.tryAcquire()) return false;
        minuteCount++;
        dayCount++;
        return true;
    }
    public void release() { concurrency.release(); }
    public synchronized void providerFailure(int status) {
        // No retry storms after quota/auth failures. These counters contain no visitor identifiers.
        cooldownUntil = status == 401 || status == 403 ? Instant.MAX
                : clock.instant().plus(status == 429 ? Duration.ofHours(24) : Duration.ofMinutes(1));
    }
}
