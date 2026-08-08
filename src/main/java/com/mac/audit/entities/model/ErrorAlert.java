package com.mac.audit.entities.model;

import java.util.UUID;

public record ErrorAlert(
        String idempotencyKey,
        String correlationId,
        String subject,
        String body) {

    public static ErrorAlert failure(String traceId, String source, String action) {
        String correlationId = traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString()
                : traceId.trim();
        String safeAction = normalize(action, "unknown-action", 50);
        String safeSource = normalize(source, "unknown-boundary", 40);
        String keyTrace = normalize(correlationId, "unknown-trace", 50);
        return new ErrorAlert(
                "audit-error-" + safeAction + "-" + keyTrace,
                normalize(correlationId, "unknown-trace", 150),
                "Audit log service error",
                "The audit-log service failed while performing '%s' at the %s boundary. Trace ID: %s."
                        .formatted(safeAction, safeSource, normalize(correlationId, "unknown-trace", 150)));
    }

    private static String normalize(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        normalized = normalized.replaceAll("[^A-Za-z0-9._:-]", "-");
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }
}
