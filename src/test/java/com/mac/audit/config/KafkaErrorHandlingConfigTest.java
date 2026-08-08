package com.mac.audit.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.audit.entities.constant.AuditOutcome;
import com.mac.audit.entities.dto.AuditEventRequest;
import com.mac.audit.utils.handler.AsyncExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaErrorHandlingConfigTest {

    @Test
    void exhaustedRecordsAreLoggedAndPublishedToDltWithTraceFallbacks() {
        AsyncExceptionHandler exceptionHandler = mock(AsyncExceptionHandler.class);
        KafkaOperations<Object, Object> operations = mock(KafkaOperations.class);
        when(operations.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        AuditEventRequest event = event();

        ConsumerRecord<String, AuditEventRequest> headerRecord = record("key", event);
        headerRecord.headers().add("X-Correlation-Id", " header-trace ".getBytes(StandardCharsets.UTF_8));
        recover(exceptionHandler, operations, headerRecord);
        verify(exceptionHandler).handle(eq("header-trace"), eq("audit-log.kafka"),
                eq("kafka-listener"), eq("consumeAuditEvent"), anyMap(), any(Exception.class));

        reset(exceptionHandler);
        recover(exceptionHandler, operations, record("key-trace", event));
        verify(exceptionHandler).handle(eq("key-trace"), anyString(), anyString(), anyString(),
                anyMap(), any(Exception.class));

        reset(exceptionHandler);
        recover(exceptionHandler, operations, record(null, event));
        verify(exceptionHandler).handle(eq(event.eventId().toString()), anyString(), anyString(), anyString(),
                anyMap(), any(Exception.class));

        reset(exceptionHandler);
        ConsumerRecord<String, Object> unknown = new ConsumerRecord<>("audit", 0, 5L, null, "unknown");
        recover(exceptionHandler, operations, unknown);
        verify(exceptionHandler).handle(argThat(trace -> trace != null && !trace.isBlank()),
                anyString(), anyString(), anyString(), anyMap(), any(Exception.class));
        verify(operations, times(4)).send(any(ProducerRecord.class));
    }

    private static void recover(AsyncExceptionHandler exceptionHandler,
            KafkaOperations<Object, Object> operations, ConsumerRecord<?, ?> record) {
        DefaultErrorHandler handler = (DefaultErrorHandler) new KafkaErrorHandlingConfig()
                .kafkaErrorHandler(exceptionHandler, operations, "audit.dlt", 0, 0);
        assertTrue(handler.handleOne(new IllegalStateException("database"), record, null, null));
    }

    private static ConsumerRecord<String, AuditEventRequest> record(String key, AuditEventRequest event) {
        return new ConsumerRecord<>("audit", 0, 5L, key, event);
    }

    private static AuditEventRequest event() {
        return new AuditEventRequest(UUID.randomUUID(), "billing", Instant.parse("2026-01-02T03:04:05Z"),
                "user", null, "login", "session", "1", AuditOutcome.SUCCESS, null, null, Map.of());
    }
}
