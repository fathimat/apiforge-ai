package ai.apiforge;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigTest {
    @Test void productionRejectsWildcardHttpMissingAndPathOrigins() {
        var env = new MockEnvironment(); env.setActiveProfiles("prod");
        for (String origin : new String[]{"", "*", "https://*.onrender.com", "http://site.example", "https://site.example/path", "https://user:pass@site.example", "https://site.example/"}) {
            assertThrows(IllegalStateException.class, () -> new WebSafetyConfig(origin, env).validateOrigins(), origin);
        }
    }
    @Test void productionAllowsExactHttpsOriginsOnly() {
        var env = new MockEnvironment(); env.setActiveProfiles("prod");
        assertDoesNotThrow(() -> new WebSafetyConfig("https://apiforge.onrender.com", env).validateOrigins());
    }
    @Test void productionRequiresVerifiedDatabaseTlsAndSeparateCredentials() {
        String good="jdbc:postgresql://ep-example.neon.tech/neondb?sslmode=verify-full&sslrootcert=/etc/ssl/certs/ca-certificates.crt";
        assertDoesNotThrow(() -> ProductionDatabaseChecks.validate(good,"role","test-only"));
        for (String bad : new String[]{"jdbc:h2:mem:test", "jdbc:postgresql://example/db", good.replace("verify-full","require"), good+"&password=secret", good+"&sslfactory=org.postgresql.ssl.NonValidatingFactory"}) {
            assertThrows(IllegalStateException.class, () -> ProductionDatabaseChecks.validate(bad,"role","test-only"));
        }
        assertThrows(IllegalStateException.class, () -> ProductionDatabaseChecks.validate(good,"role",""));
    }
}
