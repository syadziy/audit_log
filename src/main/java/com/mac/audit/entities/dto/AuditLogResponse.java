package com.mac.audit.entities.dto;

import com.mac.audit.entities.constant.AuditOutcome;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
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
        Map<String, Object> metadata) {}
