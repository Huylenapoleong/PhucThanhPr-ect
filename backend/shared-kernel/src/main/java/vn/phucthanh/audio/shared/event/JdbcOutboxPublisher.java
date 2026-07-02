package vn.phucthanh.audio.shared.event;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcOutboxPublisher implements OutboxPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOutboxPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String aggregateType, Object aggregateId, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                eventId,
                eventType,
                1,
                aggregateType,
                String.valueOf(aggregateId),
                Instant.now(),
                MDC.get("correlationId"),
                Map.of(),
                payload
        );
        jdbcTemplate.update(
                """
                insert into public.outbox_events (
                    event_id, aggregate_type, aggregate_id, event_type,
                    event_version, payload, correlation_id, status, occurred_at
                ) values (?, ?, ?, ?, ?, cast(? as jsonb), ?, 'pending', ?)
                """,
                eventId,
                aggregateType,
                String.valueOf(aggregateId),
                eventType,
                1,
                toJson(envelope),
                envelope.correlationId(),
                OffsetDateTime.ofInstant(envelope.occurredAt(), ZoneOffset.UTC)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Không thể tuần tự hóa domain event", exception);
        }
    }
}
