package ai.apiforge;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiAllowanceTest {
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-11T00:00:00Z"));
    final Clock clock = new Clock() {
        public ZoneId getZone() {return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone) {return this;}
        public Instant instant() {return now.get();}
    };
    @Test void minuteAndDayBudgetsAreHardWithinThisProcess() {
        var budget = new AiAllowance(clock);
        assertTrue(budget.tryAcquire());
        assertFalse(budget.tryAcquire()); // concurrency cap
        budget.release();
        assertTrue(budget.tryAcquire()); budget.release();
        assertFalse(budget.tryAcquire());
        now.set(now.get().plusSeconds(60));
        for (int i=2;i<20;i++) {
            assertTrue(budget.tryAcquire()); budget.release();
            now.set(now.get().plusSeconds(60));
        }
        assertFalse(budget.tryAcquire());
        now.set(Instant.parse("2026-11-12T00:00:00Z"));
        assertTrue(budget.tryAcquire()); budget.release();
    }
    @Test void quotaFailureDisablesAttemptsFor24Hours() {
        var budget = new AiAllowance(clock); budget.providerFailure(429);
        assertFalse(budget.tryAcquire());
        now.set(now.get().plus(Duration.ofHours(23))); assertFalse(budget.tryAcquire());
        now.set(now.get().plus(Duration.ofHours(1))); assertTrue(budget.tryAcquire()); budget.release();
    }
    @Test void authFailureRequiresRestartAndOtherErrorsCoolDown() {
        var auth = new AiAllowance(clock); auth.providerFailure(403);
        now.set(now.get().plus(Duration.ofDays(10))); assertFalse(auth.tryAcquire());
        var errors = new AiAllowance(clock); errors.providerFailure(500);
        assertFalse(errors.tryAcquire()); now.set(now.get().plusSeconds(60));
        assertTrue(errors.tryAcquire()); errors.release();
    }
}
