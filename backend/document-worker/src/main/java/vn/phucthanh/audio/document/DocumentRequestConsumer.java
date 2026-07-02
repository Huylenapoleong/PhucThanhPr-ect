package vn.phucthanh.audio.document;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentRequestConsumer.class);
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "quotation.document-requested.v1",
            "contract.document-requested.v1"
    );

    private final ObjectMapper objectMapper;
    private final Counter accepted;
    private final Counter ignored;

    public DocumentRequestConsumer(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.accepted = meterRegistry.counter("phucthanh.document.requests", "result", "accepted");
        this.ignored = meterRegistry.counter("phucthanh.document.requests", "result", "ignored");
    }

    @KafkaListener(
            topics = "#{'${document.worker.topics}'.split(',')}",
            groupId = "${document.worker.group-id:document-worker}"
    )
    public void consume(String rawEvent) {
        JsonNode event = parse(rawEvent);
        String eventType = event.path("eventType").asText();
        if (!SUPPORTED_EVENTS.contains(eventType)) {
            ignored.increment();
            return;
        }

        String aggregateType = event.path("aggregateType").asText();
        String aggregateId = event.path("aggregateId").asText();
        accepted.increment();
        log.info(
                "Accepted document request eventType={} aggregateType={} aggregateId={}",
                eventType,
                aggregateType,
                aggregateId
        );
        /*
         * The worker deliberately stops at the application boundary here.
         * A DOCX/PDF renderer and ObjectStoragePort adapter are plugged into the next phase;
         * no fake URL or direct write to Commerce tables is allowed.
         */
    }

    private JsonNode parse(String rawEvent) {
        try {
            return objectMapper.readTree(rawEvent);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid document event payload", exception);
        }
    }
}
