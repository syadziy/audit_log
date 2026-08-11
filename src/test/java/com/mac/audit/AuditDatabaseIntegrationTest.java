package com.mac.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mac.audit.entities.constant.AuditOutcome;
import com.mac.audit.entities.dto.AuditEventRequest;
import com.mac.audit.service.AuditLogService;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "audit.kafka.enabled=false",
    "sdk.security.enabled=false",
    "sdk.security.method-security-enabled=false",
    "sdk.security.cors.enabled=false"
})
class AuditDatabaseIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DataSource dataSource;
    @Autowired AuditLogService auditLogService;

    @Test
    void flywayCreatesAuditTableAndIndexes() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'audit_log'
                """, Integer.class);
        assertThat(tables).isEqualTo(1);
    }

    @Test
    void concurrentConsumersPersistTheSameEventOnlyOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        AuditEventRequest event = new AuditEventRequest(
                eventId,
                "api-gateway",
                Instant.parse("2026-08-11T02:00:00Z"),
                "owner",
                "owner",
                "SCHEDULER_CREATE",
                "SCHEDULER",
                null,
                AuditOutcome.SUCCESS,
                "trace-horizontal-scale",
                "127.0.0.1",
                Map.of("routeId", "scheduler"));
        List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, 20)
                .mapToObj(ignored -> (Callable<Boolean>) () -> auditLogService.record(event))
                .toList();

        List<Boolean> results;
        try (var executor = Executors.newFixedThreadPool(8)) {
            results = executor.invokeAll(attempts).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer stored = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE event_id = ?", Integer.class, eventId);
        assertThat(results).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(true),
                        java.util.stream.Stream.generate(() -> false).limit(19))
                        .toList());
        assertThat(stored).isEqualTo(1);
    }
}
