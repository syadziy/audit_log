package com.mac.audit.entities.model;

import com.mac.audit.entities.constant.AuditOutcome;
import java.time.Instant;

public record AuditLogFilter(
        Instant from,
        Instant to,
        String sourceSystem,
        String actorId,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        int limit,
        long offset) {}
