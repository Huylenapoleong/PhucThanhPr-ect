package vn.phucthanh.audio.shared.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

public class OutboxRelay {

    private static final Pattern SAFE_AGGREGATE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final List<String> aggregateTypes;
    private final int batchSize;
    private final Duration publishTimeout;

    public OutboxRelay(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            String aggregateTypes,
            int batchSize,
            Duration publishTimeout
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.aggregateTypes = parseAggregateTypes(aggregateTypes);
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        this.publishTimeout = publishTimeout;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:1000}")
    @Transactional
    public void publishPending() {
        if (aggregateTypes.isEmpty()) {
            return;
        }
        String placeholders = aggregateTypes.stream().map(ignored -> "?").collect(Collectors.joining(","));
        String sql = """
                select event_id, aggregate_type, aggregate_id, payload
                from public.outbox_events
                where status in ('pending', 'failed')
                  and (next_retry_at is null or next_retry_at <= now())
                  and aggregate_type in (%s)
                order by occurred_at
                for update skip locked
                limit ?
                """.formatted(placeholders);

        Object[] arguments = new Object[aggregateTypes.size() + 1];
        for (int index = 0; index < aggregateTypes.size(); index++) {
            arguments[index] = aggregateTypes.get(index);
        }
        arguments[arguments.length - 1] = batchSize;

        List<PendingEvent> events = jdbcTemplate.query(sql, this::mapEvent, arguments);
        for (PendingEvent event : events) {
            publish(event);
        }
    }

    private void publish(PendingEvent event) {
        try {
            kafkaTemplate.send(topic(event.aggregateType()), event.aggregateId(), event.payload())
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            jdbcTemplate.update(
                    """
                    update public.outbox_events
                    set status = 'published', published_at = now(), last_error = null
                    where event_id = ?
                    """,
                    event.eventId()
            );
        } catch (Exception exception) {
            jdbcTemplate.update(
                    """
                    update public.outbox_events
                    set status = 'failed',
                        retry_count = retry_count + 1,
                        next_retry_at = now() + interval '30 seconds',
                        last_error = left(?, 1000)
                    where event_id = ?
                    """,
                    rootMessage(exception),
                    event.eventId()
            );
        }
    }

    private PendingEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PendingEvent(
                resultSet.getObject("event_id", java.util.UUID.class),
                resultSet.getString("aggregate_type"),
                resultSet.getString("aggregate_id"),
                resultSet.getString("payload")
        );
    }

    private String topic(String aggregateType) {
        return "phucthanh."
                + aggregateType.toLowerCase(Locale.ROOT).replace('_', '-')
                + ".events";
    }

    private List<String> parseAggregateTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(SAFE_AGGREGATE.asMatchPredicate())
                .distinct()
                .toList();
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    private record PendingEvent(
            java.util.UUID eventId,
            String aggregateType,
            String aggregateId,
            String payload
    ) {
    }
}
