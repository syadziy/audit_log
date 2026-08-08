package com.mac.audit.entities.dto;

import com.mac.audit.entities.constant.AuditOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventRequest(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 100) String sourceSystem,
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 150) String actorId,
        @Size(max = 200) String actorName,
        @NotBlank @Size(max = 150) String action,
        @NotBlank @Size(max = 150) String resourceType,
        @Size(max = 200) String resourceId,
        @NotNull AuditOutcome outcome,
        @Size(max = 150) String traceId,
        @Size(max = 64) String clientIp,
        @Size(max = 50) Map<@NotBlank @Size(max = 100) String, Object> metadata) {}
