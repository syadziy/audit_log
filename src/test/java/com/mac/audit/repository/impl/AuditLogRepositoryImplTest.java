package com.mac.audit.repository.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.audit.entities.constant.AuditOutcome;
import com.mac.audit.entities.model.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class AuditLogRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void insertsIdempotentlyAndSerializesMetadata() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AuditLogRepositoryImpl repository = new AuditLogRepositoryImpl(jdbc, new ObjectMapper());
        AuditLogEntry entry = entry();
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1, 0);
        assertTrue(repository.insert(entry));
        assertFalse(repository.insert(entry));

        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JacksonException("bad") {});
        assertThrows(IllegalArgumentException.class,
                () -> new AuditLogRepositoryImpl(jdbc, broken).insert(entry));
    }

    @Test
    void findsMapsFiltersCountsAndHandlesEmptyMetadata() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AuditLogRepositoryImpl repository = new AuditLogRepositoryImpl(jdbc, new ObjectMapper());
        AuditLogEntry entry = entry();
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<AuditLogEntry>>getArgument(2)
                        .mapRow(resultSet(entry, "{\"channel\":\"api\"}"), 0)));
        assertEquals(entry, repository.findById(entry.eventId()).orElseThrow());

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<AuditLogEntry>>getArgument(2)
                        .mapRow(resultSet(entry, ""), 0)));
        AuditLogFilter all = new AuditLogFilter(NOW.minusSeconds(60), NOW.plusSeconds(1),
                " billing ", " user-1 ", " invoice.created ", " invoice ", " 42 ",
                AuditOutcome.SUCCESS, 20, 5);
        assertTrue(repository.find(all).getFirst().metadata().isEmpty());
        AuditLogFilter noOptional = new AuditLogFilter(NOW.minusSeconds(60), NOW, " ", null,
                null, null, null, null, 10, 0);
        repository.find(noOptional);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(4L, (Long) null);
        assertEquals(4, repository.count(all));
        assertEquals(0, repository.count(noOptional));
    }

    @Test
    void rejectsCorruptStoredMetadata() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new JacksonException("bad") {});
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<AuditLogEntry>>getArgument(2)
                        .mapRow(resultSet(entry(), "bad"), 0)));
        assertThrows(IllegalStateException.class,
                () -> new AuditLogRepositoryImpl(jdbc, mapper).findById(UUID.randomUUID()));
    }

    private static AuditLogEntry entry() {
        return new AuditLogEntry(UUID.fromString("11111111-1111-1111-1111-111111111111"), "billing",
                NOW.minusSeconds(1), NOW, "user-1", "Ada", "invoice.created", "invoice", "42",
                AuditOutcome.SUCCESS, "trace", "127.0.0.1", Map.of("channel", "api"));
    }

    private static ResultSet resultSet(AuditLogEntry entry, String metadata) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("event_id", UUID.class)).thenReturn(entry.eventId());
        when(rs.getString("source_system")).thenReturn(entry.sourceSystem());
        when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(entry.occurredAt()));
        when(rs.getTimestamp("received_at")).thenReturn(Timestamp.from(entry.receivedAt()));
        when(rs.getString("actor_id")).thenReturn(entry.actorId());
        when(rs.getString("actor_name")).thenReturn(entry.actorName());
        when(rs.getString("action")).thenReturn(entry.action());
        when(rs.getString("resource_type")).thenReturn(entry.resourceType());
        when(rs.getString("resource_id")).thenReturn(entry.resourceId());
        when(rs.getString("outcome")).thenReturn(entry.outcome().name());
        when(rs.getString("trace_id")).thenReturn(entry.traceId());
        when(rs.getString("client_ip")).thenReturn(entry.clientIp());
        when(rs.getString("metadata")).thenReturn(metadata);
        return rs;
    }
}
