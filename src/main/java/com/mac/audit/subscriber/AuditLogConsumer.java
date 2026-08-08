package com.mac.audit.subscriber;

import com.mac.audit.entities.constant.AuditLogFields;
import com.mac.audit.entities.dto.AuditEventRequest;
import com.mac.audit.service.AuditLogService;
import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "audit.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogConsumer.class);
    private final AuditLogService service;
    private final Validator validator;

    public AuditLogConsumer(AuditLogService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @KafkaListener(topics = "${audit.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(AuditEventRequest event,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey) {
        validate(event);
        String traceId = resolveTraceId(event, kafkaKey);
        StructuredLog.withMdc(Map.of(
                LogFields.TRACE_ID, traceId,
                LogFields.EVENT_DATASET, "audit-log.kafka"), () -> record(event));
    }

    private void record(AuditEventRequest event) {
        boolean created = service.record(event);
        StructuredLog.info(LOG, "Audit event consumed", Map.of(
                LogFields.EVENT_ACTION, "recordAuditEvent",
                LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                LogFields.EVENT_DATASET, "audit-log.kafka",
                AuditLogFields.EVENT_ID, event.eventId(),
                AuditLogFields.SOURCE_SYSTEM, event.sourceSystem(),
                AuditLogFields.ACTION, event.action(),
                AuditLogFields.OUTCOME, event.outcome().name(),
                AuditLogFields.DUPLICATE, !created));
    }

    private void validate(AuditEventRequest event) {
        Set<ConstraintViolation<AuditEventRequest>> violations = validator.validate(event);
        if (violations.isEmpty()) return;
        String message = violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted().collect(Collectors.joining(", "));
        throw new ConstraintViolationException("Kafka audit event not valid: " + message, violations);
    }

    private static String resolveTraceId(AuditEventRequest event, String kafkaKey) {
        if (event.traceId() != null && !event.traceId().isBlank()) return event.traceId().trim();
        if (kafkaKey != null && !kafkaKey.isBlank()) return kafkaKey.trim();
        return event.eventId() == null ? UUID.randomUUID().toString() : event.eventId().toString();
    }
}
