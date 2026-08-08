package com.mac.audit.repository;

import com.mac.audit.entities.model.AuditLogEntry;
import com.mac.audit.entities.model.AuditLogFilter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository {
    boolean insert(AuditLogEntry entry);
    Optional<AuditLogEntry> findById(UUID eventId);
    List<AuditLogEntry> find(AuditLogFilter filter);
    long count(AuditLogFilter filter);
}
