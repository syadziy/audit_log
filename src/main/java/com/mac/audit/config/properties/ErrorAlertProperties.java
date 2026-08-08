package com.mac.audit.config.properties;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("audit.error-alert")
public record ErrorAlertProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:9001/api/v1/alert") URI endpoint,
        @DefaultValue("audit-log@example.com") String senderEmail,
        @DefaultValue("Audit Log Service") String senderName,
        @DefaultValue("ops@example.com") List<String> recipients,
        @DefaultValue("") String authorizationHeader,
        @DefaultValue("5s") Duration timeout) {

    public ErrorAlertProperties {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        authorizationHeader = authorizationHeader == null ? "" : authorizationHeader;
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("audit.error-alert.timeout must be positive");
        }
    }
}
