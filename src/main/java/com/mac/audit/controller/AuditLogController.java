package com.mac.audit.controller;

import com.mac.audit.config.properties.AuditQueryProperties;
import com.mac.audit.entities.constant.AuditOutcome;
import com.mac.audit.entities.dto.AuditLogResponse;
import com.mac.audit.entities.model.AuditLogFilter;
import com.mac.audit.service.AuditLogService;
import com.mac.sdk_util.entities.dto.PagingDTO;
import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.entities.constant.Role;
import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.sdk_util.helper.ResponsePagingHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService service;
    private final ZoneId applicationZone;
    private final Clock clock;
    private final AuditQueryProperties properties;

    public AuditLogController(AuditLogService service, ZoneId applicationZone, Clock clock,
            AuditQueryProperties properties) {
        this.service = service;
        this.applicationZone = applicationZone;
        this.clock = clock;
        this.properties = properties;
    }

    @GetMapping("/{eventId}")
    @PreAuthorize(Role.AUDIT_READ)
    public ResponseEntity<ResponseDTO<AuditLogResponse>> findById(@PathVariable UUID eventId) {
        return ResponseHelper.httpOK(service.findById(eventId));
    }

    @GetMapping
    @PreAuthorize(Role.AUDIT_READ)
    public ResponseEntity<ResponseDTO<List<AuditLogResponse>>> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) long offset) {
        AuditLogFilter filter = createFilter(date, from, to, sourceSystem, actorId, action,
                resourceType, resourceId, outcome, limit, offset);
        return ResponsePagingHelper.httpOK(
                service.find(filter), new PagingDTO(limit, offset, service.count(filter)));
    }

    private AuditLogFilter createFilter(LocalDate date, Instant from, Instant to,
            String sourceSystem, String actorId, String action, String resourceType,
            String resourceId, AuditOutcome outcome, int limit, long offset) {
        if (date != null && (from != null || to != null)) {
            throw new IllegalArgumentException("date cannot be combined with from or to");
        }
        Instant resolvedTo = null;
        Instant resolvedFrom = null;
        if (date != null) {
            resolvedFrom = date.atStartOfDay(applicationZone).toInstant();
            resolvedTo = date.plusDays(1).atStartOfDay(applicationZone).toInstant();
        } else if (from != null || to != null) {
            resolvedTo = to == null ? clock.instant() : to;
            resolvedFrom = from == null ? resolvedTo.minus(Duration.ofDays(1)) : from;
        }
        if (resolvedFrom != null && resolvedTo != null && !resolvedFrom.isBefore(resolvedTo)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }
        if (resolvedFrom != null && resolvedTo != null
                && Duration.between(resolvedFrom, resolvedTo).compareTo(properties.maxRange()) > 0) {
            throw new IllegalArgumentException("audit log range must not exceed " + properties.maxRange());
        }
        return new AuditLogFilter(resolvedFrom, resolvedTo, sourceSystem, actorId, action,
                resourceType, resourceId, outcome, limit, offset);
    }
}
