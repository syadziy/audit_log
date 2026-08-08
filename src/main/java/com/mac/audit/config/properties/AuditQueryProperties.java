package com.mac.audit.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audit.query")
public record AuditQueryProperties(Duration maxRange) {

    public AuditQueryProperties {
        maxRange = maxRange == null ? Duration.ofDays(31) : maxRange;
        if (maxRange.isZero() || maxRange.isNegative()) {
            throw new IllegalArgumentException("audit.query.max-range must be positive");
        }
    }
}
