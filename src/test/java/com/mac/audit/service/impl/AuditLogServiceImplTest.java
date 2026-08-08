package com.mac.audit.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mac.audit.entities.constant.AuditOutcome;
import com.mac.audit.entities.dto.AuditEventRequest;
import com.mac.audit.entities.model.*;
import com.mac.audit.repository.AuditLogRepository;
import com.mac.sdk_util.exception.ResourceNotFoundException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuditLogServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void recordsNormalizedAppendOnlyEntry() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogServiceImpl service = new AuditLogServiceImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        AuditEventRequest request = new AuditEventRequest(UUID.randomUUID(), " billing ", NOW.minusSeconds(1),
                " user-1 ", " ", " invoice.created ", " invoice ", " 42 ", AuditOutcome.SUCCESS,
                " trace ", " 127.0.0.1 ", null);
        when(repository.insert(any())).thenReturn(true);
        assertTrue(service.record(request));
        var entry = org.mockito.ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).insert(entry.capture());
        assertEquals("billing", entry.getValue().sourceSystem());
        assertNull(entry.getValue().actorName());
        assertEquals("42", entry.getValue().resourceId());
        assertEquals(Map.of(), entry.getValue().metadata());
        assertEquals(NOW, entry.getValue().receivedAt());

        Map<String, Object> metadataWithNull = new LinkedHashMap<>();
        metadataWithNull.put("key", "value");
        metadataWithNull.put("nullable", null);
        AuditLogEntry nullableMetadata = new AuditLogEntry(UUID.randomUUID(), "billing", NOW, NOW,
                "user", null, "read", "invoice", null, AuditOutcome.SUCCESS, null, null,
                metadataWithNull);
        assertNull(nullableMetadata.metadata().get("nullable"));
        assertThrows(UnsupportedOperationException.class,
                () -> nullableMetadata.metadata().put("new", "value"));
    }

    @Test
    void mapsFindListCountAndMissingEvent() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogServiceImpl service = new AuditLogServiceImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        AuditLogEntry entry = entry();
        AuditLogFilter filter = new AuditLogFilter(NOW.minusSeconds(60), NOW, null, null, null,
                null, null, null, 10, 0);
        when(repository.findById(entry.eventId())).thenReturn(Optional.of(entry));
        when(repository.find(filter)).thenReturn(List.of(entry));
        when(repository.count(filter)).thenReturn(3L);
        assertEquals(entry.eventId(), service.findById(entry.eventId()).eventId());
        assertEquals(entry.metadata(), service.find(filter).getFirst().metadata());
        assertEquals(3, service.count(filter));
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(missing));
    }

    static AuditLogEntry entry() {
        return new AuditLogEntry(UUID.randomUUID(), "billing", NOW.minusSeconds(1), NOW, "user-1",
                "Ada", "invoice.created", "invoice", "42", AuditOutcome.SUCCESS,
                "trace", "127.0.0.1", Map.of("channel", "api"));
    }
}
