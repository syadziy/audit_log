package com.mac.audit.config;

import com.mac.audit.entities.constant.AuditLogFields;
import com.mac.audit.entities.dto.AuditEventRequest;
import com.mac.audit.utils.handler.AsyncExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaErrorHandlingConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            AsyncExceptionHandler exceptionHandler,
            KafkaOperations<Object, Object> kafkaOperations,
            @Value("${audit.kafka.dead-letter-topic}") String deadLetterTopic,
            @Value("${audit.kafka.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${audit.kafka.max-retries:2}") long maxRetries) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations, (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            exceptionHandler.handle(resolveTraceId(record), "audit-log.kafka", "kafka-listener",
                    "consumeAuditEvent", fields(record, deadLetterTopic), exception);
            recoverer.accept(record, exception);
        }, new FixedBackOff(retryIntervalMs, maxRetries));
        handler.addNotRetryableExceptions(ConstraintViolationException.class, IllegalArgumentException.class);
        return handler;
    }

    private static Map<String, Object> fields(ConsumerRecord<?, ?> record, String deadLetterTopic) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(AuditLogFields.KAFKA_TOPIC, record.topic());
        fields.put(AuditLogFields.KAFKA_PARTITION, record.partition());
        fields.put(AuditLogFields.KAFKA_OFFSET, record.offset());
        fields.put(AuditLogFields.KAFKA_DLT_TOPIC, deadLetterTopic);
        return fields;
    }

    private static String resolveTraceId(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader("X-Correlation-Id");
        if (header != null && header.value() != null) {
            String value = new String(header.value(), StandardCharsets.UTF_8).trim();
            if (!value.isBlank()) return value;
        }
        if (record.key() != null && !record.key().toString().isBlank()) return record.key().toString();
        if (record.value() instanceof AuditEventRequest event && event.eventId() != null) {
            return event.eventId().toString();
        }
        return UUID.randomUUID().toString();
    }
}
