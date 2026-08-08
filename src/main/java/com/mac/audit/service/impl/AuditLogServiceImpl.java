package com.mac.audit.service.impl;

import com.mac.audit.entities.dto.AuditEventRequest;
import com.mac.audit.entities.dto.AuditLogResponse;
import com.mac.audit.entities.model.AuditLogEntry;
import com.mac.audit.entities.model.AuditLogFilter;
import com.mac.audit.repository.AuditLogRepository;
import com.mac.audit.service.AuditLogService;
import com.mac.sdk_util.exception.ResourceNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditLogServiceImpl(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean record(AuditEventRequest request) {
        AuditLogEntry entry = new AuditLogEntry(
                request.eventId(), request.sourceSystem().trim(), request.occurredAt(), clock.instant(),
                request.actorId().trim(), trimToNull(request.actorName()), request.action().trim(),
                request.resourceType().trim(), trimToNull(request.resourceId()), request.outcome(),
                trimToNull(request.traceId()), trimToNull(request.clientIp()),
                request.metadata() == null ? Map.of() : request.metadata());
        return repository.insert(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse findById(UUID eventId) {
        return repository.findById(eventId)
                .map(AuditLogServiceImpl::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> find(AuditLogFilter filter) {
        return repository.find(filter).stream().map(AuditLogServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count(AuditLogFilter filter) {
        return repository.count(filter);
    }

    private static AuditLogResponse toResponse(AuditLogEntry entry) {
        return new AuditLogResponse(
                entry.eventId(), entry.sourceSystem(), entry.occurredAt(), entry.receivedAt(),
                entry.actorId(), entry.actorName(), entry.action(), entry.resourceType(),
                entry.resourceId(), entry.outcome(), entry.traceId(), entry.clientIp(), entry.metadata());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
