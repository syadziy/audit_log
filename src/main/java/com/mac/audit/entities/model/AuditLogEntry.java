package com.mac.audit.entities.model;

import com.mac.audit.entities.constant.AuditOutcome;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.LinkedHashMap;

public record AuditLogEntry(
        UUID eventId,
        String sourceSystem,
        Instant occurredAt,
        Instant receivedAt,
        String actorId,
        String actorName,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String traceId,
        String clientIp,
        Map<String, Object> metadata) {

    public AuditLogEntry {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
