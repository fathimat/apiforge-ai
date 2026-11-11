package ai.apiforge;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionDatabaseChecks {
    /** Run before datasource beans can connect using an accidentally insecure URL. */
    @Bean
    static BeanFactoryPostProcessor requireEncryptedDatabase(Environment environment) {
        return factory -> validate(environment.getRequiredProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.datasource.username"),
                environment.getRequiredProperty("spring.datasource.password"));
    }

    static void validate(String url, String user, String password) {
        if (user.isBlank() || password.isBlank()) throw new IllegalStateException("Database credentials must be supplied securely in production");
        try {
            if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI uri = URI.create(url.substring(5));
            if (uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
            Map<String, String> query = new HashMap<>();
            for (String entry : (uri.getRawQuery() == null ? "" : uri.getRawQuery()).split("&")) {
                var pair = entry.split("=", 2);
                if (pair.length == 2) query.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            if (!"verify-full".equals(query.get("sslmode")) || query.getOrDefault("sslrootcert", "").isBlank()
                    || query.containsKey("user") || query.containsKey("password")
                    || query.containsKey("sslfactory") || query.containsKey("sslhostnameverifier")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Production requires a PostgreSQL JDBC URL with sslmode=verify-full and sslrootcert, and credentials supplied separately");
        }
    }
}
