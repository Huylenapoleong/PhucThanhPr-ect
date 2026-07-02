package vn.phucthanh.audio.shared.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        Map<String, String> metadata,
        T payload
) {
    public EventEnvelope {
        eventId = eventId == null ? UUID.randomUUID() : eventId;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
