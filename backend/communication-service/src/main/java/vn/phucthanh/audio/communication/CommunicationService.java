package vn.phucthanh.audio.communication;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phucthanh.audio.shared.domain.BusinessCodes;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.shared.web.BusinessException;

@Service
public class CommunicationService {

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public CommunicationService(NamedParameterJdbcTemplate jdbc, OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional
    public Map<String, Object> createCall(CreateCall command) {
        String code = BusinessCodes.next("CG");
        Long id = jdbc.queryForObject(
                """
                insert into public.call_logs (
                    call_code, external_call_id, call_provider, source_channel,
                    customer_id, direction, phone_number, caller_name, started_at,
                    status, interaction_mode, route, result, external_payload
                ) values (
                    :code, :externalCallId, :provider, :sourceChannel,
                    :customerId, :direction, :phone, :callerName, now(),
                    'queued', :interactionMode, :route, 'pending', cast(:payload as jsonb)
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("externalCallId", command.externalCallId())
                        .addValue("provider", command.provider())
                        .addValue("sourceChannel", command.sourceChannel())
                        .addValue("customerId", command.customerId())
                        .addValue("direction", command.direction())
                        .addValue("phone", command.phoneNumber())
                        .addValue("callerName", command.callerName())
                        .addValue("interactionMode", command.interactionMode())
                        .addValue("route", command.route())
                        .addValue("payload", command.externalPayload()),
                Long.class
        );
        outbox.publish("CALL", id, "call.created.v1", Map.of("callId", id, "callCode", code));
        return row("call_logs", id, "CALL_NOT_FOUND");
    }

    @Transactional
    public Map<String, Object> completeCall(long id, CompleteCall command) {
        int updated = jdbc.update(
                """
                update public.call_logs
                set status = 'completed',
                    answered_at = coalesce(answered_at, started_at),
                    ended_at = now(),
                    duration_seconds = greatest(0, extract(epoch from (now() - started_at))::integer),
                    intent = :intent,
                    intent_confidence = :confidence,
                    customer_requirement = :requirement,
                    transcript = :transcript,
                    ai_summary = :summary,
                    ai_resolution = :resolution,
                    result = :result,
                    handoff_required = :handoffRequired,
                    transfer_reason = :transferReason,
                    lead_id = :leadId,
                    repair_request_id = :repairId,
                    customer_reminder_id = :reminderId,
                    version = version + 1,
                    updated_at = now()
                where id = :id and version = :version
                  and status in ('queued', 'ringing', 'in_progress')
                """,
                new MapSqlParameterSource()
                        .addValue("intent", command.intent())
                        .addValue("confidence", command.intentConfidence())
                        .addValue("requirement", command.customerRequirement())
                        .addValue("transcript", command.transcript())
                        .addValue("summary", command.aiSummary())
                        .addValue("resolution", command.aiResolution())
                        .addValue("result", command.result())
                        .addValue("handoffRequired", command.handoffRequired())
                        .addValue("transferReason", command.transferReason())
                        .addValue("leadId", command.leadId())
                        .addValue("repairId", command.repairRequestId())
                        .addValue("reminderId", command.reminderId())
                        .addValue("id", id)
                        .addValue("version", command.version())
        );
        if (updated == 0) {
            throw new BusinessException(409, "CALL_NOT_COMPLETABLE", "Cuộc gọi không thể hoàn tất hoặc vừa được cập nhật");
        }
        outbox.publish("CALL", id, "call.completed.v1", Map.of("callId", id, "result", command.result()));
        return row("call_logs", id, "CALL_NOT_FOUND");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> calls(String status, int limit) {
        return jdbc.queryForList(
                """
                select * from public.call_logs
                where (:status is null or status = :status)
                order by started_at desc, id desc
                limit :limit
                """,
                new MapSqlParameterSource()
                        .addValue("status", status == null || status.isBlank() ? null : status)
                        .addValue("limit", Math.max(1, Math.min(limit, 200)))
        );
    }

    @Transactional
    public Map<String, Object> queueMessage(QueueMessage command) {
        if (command.idempotencyKey() != null) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "select * from public.message_logs where idempotency_key = :key",
                    Map.of("key", command.idempotencyKey())
            );
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
        }
        String code = BusinessCodes.next("TN");
        Long id = jdbc.queryForObject(
                """
                insert into public.message_logs (
                    message_code, customer_id, reminder_id, channel, direction,
                    recipient, subject, content, status, idempotency_key
                ) values (
                    :code, :customerId, :reminderId, :channel, 'outbound',
                    :recipient, :subject, :content, 'queued', :idempotencyKey
                )
                on conflict do nothing
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("customerId", command.customerId())
                        .addValue("reminderId", command.reminderId())
                        .addValue("channel", command.channel())
                        .addValue("recipient", command.recipient())
                        .addValue("subject", command.subject())
                        .addValue("content", command.content())
                        .addValue("idempotencyKey", command.idempotencyKey()),
                Long.class
        );
        outbox.publish("MESSAGE", id, "message.queued.v1", Map.of("messageId", id, "channel", command.channel()));
        return row("message_logs", id, "MESSAGE_NOT_FOUND");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> messages(String status, int limit) {
        return jdbc.queryForList(
                """
                select * from public.message_logs
                where (:status is null or status = :status)
                order by created_at desc, id desc
                limit :limit
                """,
                new MapSqlParameterSource()
                        .addValue("status", status == null || status.isBlank() ? null : status)
                        .addValue("limit", Math.max(1, Math.min(limit, 200)))
        );
    }

    private Map<String, Object> row(String table, long id, String code) {
        if (!List.of("call_logs", "message_logs").contains(table)) {
            throw new IllegalArgumentException("Unsupported table");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from public." + table + " where id = :id",
                Map.of("id", id)
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, code, "Không tìm thấy dữ liệu " + id);
        }
        return rows.get(0);
    }

    public record CreateCall(
            String externalCallId, String provider, String sourceChannel,
            Long customerId, String direction, String phoneNumber,
            String callerName, String interactionMode, String route,
            String externalPayload
    ) {
    }

    public record CompleteCall(
            String intent, BigDecimal intentConfidence, String customerRequirement,
            String transcript, String aiSummary, String aiResolution, String result,
            boolean handoffRequired, String transferReason, Long leadId,
            Long repairRequestId, Long reminderId, long version
    ) {
    }

    public record QueueMessage(
            Long customerId, Long reminderId, String channel, String recipient,
            String subject, String content, String idempotencyKey
    ) {
    }
}
