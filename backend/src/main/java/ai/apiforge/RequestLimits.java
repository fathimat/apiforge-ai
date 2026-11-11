package ai.apiforge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.limits")
public record RequestLimits(
        @DefaultValue("131072") @Min(1024) @Max(262144) int maxBodyBytes,
        @DefaultValue("120") @Min(1) @Max(1000) int globalPerMinute,
        @DefaultValue("30") @Min(1) @Max(300) int peerPerMinute,
        @DefaultValue("4") @Min(1) @Max(8) int maxConcurrent) {}
