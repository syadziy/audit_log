package com.mac.audit.subscriber;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mac.audit.entities.constant.AuditOutcome;
import com.mac.audit.entities.dto.AuditEventRequest;
import com.mac.audit.service.AuditLogService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuditLogConsumerTest {

    @Test
    void validatesRecordsDuplicatesAndResolvesTraceFallbacks() {
        AuditLogService service = mock(AuditLogService.class);
        AuditLogConsumer consumer = new AuditLogConsumer(service,
                Validation.buildDefaultValidatorFactory().getValidator());
        AuditEventRequest traced = event(UUID.randomUUID(), " trace ");
        when(service.record(traced)).thenReturn(true);
        consumer.consume(traced, "key");
        verify(service).record(traced);

        AuditEventRequest keyed = event(UUID.randomUUID(), null);
        when(service.record(keyed)).thenReturn(false);
        consumer.consume(keyed, " key ");
        AuditEventRequest idFallback = event(UUID.randomUUID(), " ");
        consumer.consume(idFallback, null);

        AuditEventRequest invalid = new AuditEventRequest(null, "", null, "", null, "", "",
                null, null, null, null, null);
        assertThrows(ConstraintViolationException.class, () -> consumer.consume(invalid, null));
    }

    private static AuditEventRequest event(UUID id, String trace) {
        return new AuditEventRequest(id, "billing", Instant.parse("2026-01-02T03:04:05Z"),
                "user-1", "Ada", "invoice.created", "invoice", "42", AuditOutcome.SUCCESS,
                trace, "127.0.0.1", Map.of("channel", "api"));
    }
}
