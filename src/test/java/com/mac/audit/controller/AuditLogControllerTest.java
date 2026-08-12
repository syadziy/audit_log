package com.mac.audit.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.audit.config.properties.AuditQueryProperties;
import com.mac.audit.entities.constant.AuditOutcome;
import com.mac.audit.entities.dto.AuditLogResponse;
import com.mac.audit.entities.model.AuditLogFilter;
import com.mac.audit.service.AuditLogService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuditLogControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void findsByIdAndBuildsDefaultExplicitAndDateFilters() {
        AuditLogService service = mock(AuditLogService.class);
        AuditLogController controller = controller(service);
        UUID id = UUID.randomUUID();
        AuditLogResponse response = response(id);
        when(service.findById(id)).thenReturn(response);
        assertEquals(response, controller.findById(id).getBody().getData());

        when(service.find(any())).thenReturn(List.of(response));
        when(service.count(any())).thenReturn(1L);
        var defaultResult = controller.find(null, null, null, "billing", "user-1", null,
                null, null, AuditOutcome.SUCCESS, 50, 0);
        assertEquals(1, defaultResult.getBody().getPaging().getTotalRecord());
        var filter = org.mockito.ArgumentCaptor.forClass(AuditLogFilter.class);
        verify(service).find(filter.capture());
        assertNull(filter.getValue().from());
        assertNull(filter.getValue().to());

        controller.find(LocalDate.of(2026, 1, 2), null, null, null, null, null,
                null, null, null, 10, 2);
        controller.find(null, NOW.minusSeconds(60), NOW, null, null, "login",
                "session", "abc", AuditOutcome.DENIED, 10, 0);
    }

    @Test
    void rejectsAmbiguousInvalidAndOversizedRanges() {
        AuditLogController controller = controller(mock(AuditLogService.class));
        assertThrows(IllegalArgumentException.class, () -> controller.find(
                LocalDate.now(), NOW.minusSeconds(1), null, null, null, null, null, null, null, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.find(
                null, NOW, NOW, null, null, null, null, null, null, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.find(
                null, NOW.minus(Duration.ofDays(32)), NOW, null, null, null, null, null, null, 10, 0));
    }

    private static AuditLogController controller(AuditLogService service) {
        return new AuditLogController(service, ZoneOffset.UTC, Clock.fixed(NOW, ZoneOffset.UTC),
                new AuditQueryProperties(Duration.ofDays(31)));
    }

    private static AuditLogResponse response(UUID id) {
        return new AuditLogResponse(id, "billing", NOW, NOW, "user-1", "Ada", "login",
                "session", "abc", AuditOutcome.SUCCESS, "trace", null, Map.of());
    }
}
