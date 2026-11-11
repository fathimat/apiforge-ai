package ai.apiforge;

import com.fasterxml.jackson.core.StreamReadConstraints;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableConfigurationProperties(RequestLimits.class)
public class WebSafetyConfig {
    private final List<String> origins;
    private final Environment environment;

    public WebSafetyConfig(@Value("${app.cors.allowed-origins:}") String origins, Environment environment) {
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.environment = environment;
    }

    @PostConstruct
    void validateOrigins() {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (prod && origins.isEmpty()) throw new IllegalStateException("Configure an exact HTTPS frontend origin in production");
        for (String origin : origins) {
            URI uri;
            try { uri = URI.create(origin); } catch (IllegalArgumentException e) { throw new IllegalStateException("Invalid CORS origin"); }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty()) || origin.contains("*")
                    || !("https".equals(uri.getScheme()) || (!prod && "http".equals(uri.getScheme())))) {
                throw new IllegalStateException("CORS requires exact origins without paths; production requires HTTPS");
            }
        }
    }

    @Bean
    FilterRegistrationBean<PublicSafetyFilter> publicSafety(RequestLimits limits, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        var filter = new PublicSafetyFilter(limits, new RequestRateLimiter(limits, Clock.systemUTC()), mapper);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<CorsFilter> apiCors() {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        cors.setExposedHeaders(List.of("Retry-After"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        var registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer jsonConstraints() {
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(50_000)
                        .maxNumberLength(1000).maxDocumentLength(131072).build()));
    }
}
