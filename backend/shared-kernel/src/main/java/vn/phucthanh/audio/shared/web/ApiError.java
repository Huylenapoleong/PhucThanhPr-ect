package vn.phucthanh.audio.shared.web;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, Object> details
) {
    public ApiError {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
