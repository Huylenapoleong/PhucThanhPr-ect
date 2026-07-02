package vn.phucthanh.audio.usercrm.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_audit_logs", schema = "public")
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private boolean success;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected AuthAuditLog() {
    }

    public static AuthAuditLog success(
            UUID userId,
            String eventType,
            Map<String, Object> metadata
    ) {
        return success(userId, null, eventType, "web", metadata);
    }

    public static AuthAuditLog success(
            UUID userId,
            UUID actorUserId,
            String eventType,
            String source,
            Map<String, Object> metadata
    ) {
        AuthAuditLog log = new AuthAuditLog();
        log.userId = userId;
        log.actorUserId = actorUserId;
        log.eventType = eventType;
        log.source = source;
        log.success = true;
        log.metadata = metadata == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(metadata);
        return log;
    }

    @PrePersist
    void prePersist() {
        occurredAt = occurredAt == null ? OffsetDateTime.now() : occurredAt;
    }
}
