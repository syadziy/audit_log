package com.mac.audit.repository.impl;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.mac.audit.entities.constant.AuditOutcome;
import com.mac.audit.entities.model.AuditLogEntry;
import com.mac.audit.entities.model.AuditLogFilter;
import com.mac.audit.repository.AuditLogRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private static final String COLUMNS = """
            event_id, source_system, occurred_at, received_at, actor_id, actor_name,
            action, resource_type, resource_id, outcome, trace_id, client_ip, metadata
            """;
    private static final String FILTER_BASE = " FROM audit_log WHERE occurred_at >= :from AND occurred_at < :to";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean insert(AuditLogEntry entry) {
        int updated = jdbcTemplate.update("""
                INSERT INTO audit_log (
                    event_id, source_system, occurred_at, received_at, actor_id, actor_name,
                    action, resource_type, resource_id, outcome, trace_id, client_ip, metadata
                ) VALUES (
                    :eventId, :sourceSystem, :occurredAt, :receivedAt, :actorId, :actorName,
                    :action, :resourceType, :resourceId, :outcome, :traceId, :clientIp,
                    CAST(:metadata AS jsonb)
                ) ON CONFLICT (event_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("eventId", entry.eventId())
                .addValue("sourceSystem", entry.sourceSystem())
                .addValue("occurredAt", Timestamp.from(entry.occurredAt()))
                .addValue("receivedAt", Timestamp.from(entry.receivedAt()))
                .addValue("actorId", entry.actorId())
                .addValue("actorName", entry.actorName())
                .addValue("action", entry.action())
                .addValue("resourceType", entry.resourceType())
                .addValue("resourceId", entry.resourceId())
                .addValue("outcome", entry.outcome().name())
                .addValue("traceId", entry.traceId())
                .addValue("clientIp", entry.clientIp())
                .addValue("metadata", writeMetadata(entry.metadata())));
        return updated == 1;
    }

    @Override
    public Optional<AuditLogEntry> findById(UUID eventId) {
        return jdbcTemplate.query(
                        "SELECT " + COLUMNS + " FROM audit_log WHERE event_id = :eventId",
                        java.util.Map.of("eventId", eventId), this::mapEntry)
                .stream().findFirst();
    }

    @Override
    public List<AuditLogEntry> find(AuditLogFilter filter) {
        Query query = buildFilter(filter);
        query.parameters().addValue("limit", filter.limit()).addValue("offset", filter.offset());
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + query.sql() + " ORDER BY occurred_at DESC, event_id DESC"
                        + " LIMIT :limit OFFSET :offset",
                query.parameters(), this::mapEntry);
    }

    @Override
    public long count(AuditLogFilter filter) {
        Query query = buildFilter(filter);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + query.sql(), query.parameters(), Long.class);
        return count == null ? 0 : count;
    }

    private Query buildFilter(AuditLogFilter filter) {
        StringBuilder sql = new StringBuilder(FILTER_BASE);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(filter.from())).addValue("to", Timestamp.from(filter.to()));
        List<String> predicates = new ArrayList<>();
        add(predicates, parameters, "source_system = :sourceSystem", "sourceSystem", filter.sourceSystem());
        add(predicates, parameters, "actor_id = :actorId", "actorId", filter.actorId());
        add(predicates, parameters, "action = :action", "action", filter.action());
        add(predicates, parameters, "resource_type = :resourceType", "resourceType", filter.resourceType());
        add(predicates, parameters, "resource_id = :resourceId", "resourceId", filter.resourceId());
        if (filter.outcome() != null) {
            predicates.add("outcome = :outcome");
            parameters.addValue("outcome", filter.outcome().name());
        }
        predicates.forEach(predicate -> sql.append(" AND ").append(predicate));
        return new Query(sql.toString(), parameters);
    }

    private static void add(List<String> predicates, MapSqlParameterSource parameters,
            String predicate, String name, String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(predicate);
            parameters.addValue(name, value.trim());
        }
    }

    private AuditLogEntry mapEntry(ResultSet rs, int rowNumber) throws SQLException {
        return new AuditLogEntry(
                rs.getObject("event_id", UUID.class), rs.getString("source_system"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("received_at").toInstant(),
                rs.getString("actor_id"), rs.getString("actor_name"), rs.getString("action"),
                rs.getString("resource_type"), rs.getString("resource_id"),
                AuditOutcome.valueOf(rs.getString("outcome")), rs.getString("trace_id"),
                rs.getString("client_ip"), readMetadata(rs.getString("metadata")));
    }

    private String writeMetadata(java.util.Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Audit metadata cannot be serialized", exception);
        }
    }

    private java.util.Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) return java.util.Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored audit metadata cannot be read", exception);
        }
    }

    private record Query(String sql, MapSqlParameterSource parameters) {}
}
