package com.mac.audit.service;

import com.mac.audit.entities.dto.AuditEventRequest;
import com.mac.audit.entities.dto.AuditLogResponse;
import com.mac.audit.entities.model.AuditLogFilter;
import java.util.List;
import java.util.UUID;

public interface AuditLogService {
    boolean record(AuditEventRequest request);
    AuditLogResponse findById(UUID eventId);
    List<AuditLogResponse> find(AuditLogFilter filter);
    long count(AuditLogFilter filter);
}
