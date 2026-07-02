package vn.phucthanh.audio.commerce.lead;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phucthanh.audio.shared.domain.BusinessCodes;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.shared.web.BusinessException;

@Service
public class LeadService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "new", Set.of("contacted", "lost"),
            "contacted", Set.of("consulting", "lost"),
            "consulting", Set.of("quoted", "lost"),
            "quoted", Set.of("negotiating", "won", "lost"),
            "negotiating", Set.of("won", "lost"),
            "won", Set.of("delivering", "collecting", "closed"),
            "delivering", Set.of("collecting", "closed"),
            "collecting", Set.of("closed")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public LeadService(NamedParameterJdbcTemplate jdbc, OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String stage, UUID assignedTo, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        return jdbc.queryForList(
                """
                select id, lead_code, customer_id, name, source, contact_name,
                       contact_phone, contact_email, score, temperature, stage,
                       estimated_value, expected_close_date, assigned_to_user_id,
                       next_follow_up_at, lost_reason, version, created_at, updated_at
                from public.leads
                where (:stage is null or stage = :stage)
                  and (:assignedTo is null or assigned_to_user_id = :assignedTo)
                order by updated_at desc, id desc
                limit :limit offset :offset
                """,
                new MapSqlParameterSource()
                        .addValue("stage", blankToNull(stage))
                        .addValue("assignedTo", assignedTo)
                        .addValue("limit", safeSize)
                        .addValue("offset", Math.max(0, page) * safeSize)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from public.leads where id = :id",
                Map.of("id", id)
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "LEAD_NOT_FOUND", "Không tìm thấy lead " + id);
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("items", jdbc.queryForList(
                "select * from public.lead_items where lead_id = :id order by sort_order, id",
                Map.of("id", id)
        ));
        return result;
    }

    @Transactional
    public Map<String, Object> create(CreateLead command) {
        String code = BusinessCodes.next("LD");
        Long id = jdbc.queryForObject(
                """
                insert into public.leads (
                    lead_code, customer_id, name, source, contact_name,
                    contact_phone, contact_email, company_name, tax_code,
                    project_url, requirement, score, temperature, stage,
                    estimated_value, expected_close_date, assigned_to_user_id,
                    next_follow_up_at, origin_ref_type, origin_ref_id,
                    origin_payload, notes
                ) values (
                    :code, :customerId, :name, :source, :contactName,
                    :contactPhone, :contactEmail, :companyName, :taxCode,
                    :projectUrl, :requirement, :score, :temperature, 'new',
                    :estimatedValue, :expectedCloseDate, :assignedTo,
                    :nextFollowUpAt, :originRefType, :originRefId,
                    cast(:originPayload as jsonb), :notes
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("customerId", command.customerId())
                        .addValue("name", command.name().trim())
                        .addValue("source", command.source())
                        .addValue("contactName", command.contactName())
                        .addValue("contactPhone", command.contactPhone())
                        .addValue("contactEmail", command.contactEmail())
                        .addValue("companyName", command.companyName())
                        .addValue("taxCode", command.taxCode())
                        .addValue("projectUrl", command.projectUrl())
                        .addValue("requirement", command.requirement())
                        .addValue("score", command.score())
                        .addValue("temperature", command.temperature())
                        .addValue("estimatedValue", command.estimatedValue())
                        .addValue("expectedCloseDate", command.expectedCloseDate())
                        .addValue("assignedTo", command.assignedTo())
                        .addValue("nextFollowUpAt", command.nextFollowUpAt())
                        .addValue("originRefType", command.originRefType())
                        .addValue("originRefId", command.originRefId())
                        .addValue("originPayload", command.originPayload())
                        .addValue("notes", command.notes()),
                Long.class
        );
        int sortOrder = 0;
        for (LeadItem item : command.items()) {
            jdbc.update(
                    """
                    insert into public.lead_items (
                        lead_id, product_id, product_sku, product_name, unit,
                        quantity, unit_price, discount_amount, notes, sort_order
                    ) values (
                        :leadId, :productId, :sku, :name, :unit,
                        :quantity, :unitPrice, :discount, :notes, :sortOrder
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("leadId", id)
                            .addValue("productId", item.productId())
                            .addValue("sku", item.productSku())
                            .addValue("name", item.productName())
                            .addValue("unit", item.unit())
                            .addValue("quantity", item.quantity())
                            .addValue("unitPrice", item.unitPrice())
                            .addValue("discount", item.discountAmount())
                            .addValue("notes", item.notes())
                            .addValue("sortOrder", sortOrder++)
            );
        }
        outbox.publish("LEAD", id, "lead.created.v1", Map.of("leadId", id, "leadCode", code));
        return get(id);
    }

    @Transactional
    public Map<String, Object> transition(long id, String target, String lostReason, long expectedVersion) {
        Map<String, Object> current = get(id);
        String stage = (String) current.get("stage");
        if (!TRANSITIONS.getOrDefault(stage, Set.of()).contains(target)) {
            throw new BusinessException(
                    409,
                    "INVALID_LEAD_TRANSITION",
                    "Không thể chuyển lead từ " + stage + " sang " + target
            );
        }
        if ("lost".equals(target) && (lostReason == null || lostReason.isBlank())) {
            throw new BusinessException(400, "LOST_REASON_REQUIRED", "Cần nhập lý do mất lead");
        }
        int updated = jdbc.update(
                """
                update public.leads
                set stage = :target,
                    lost_reason = :lostReason,
                    closed_at = case when :target in ('closed', 'lost') then now() else closed_at end,
                    last_activity_at = now(),
                    version = version + 1,
                    updated_at = now()
                where id = :id and version = :version
                """,
                new MapSqlParameterSource()
                        .addValue("target", target)
                        .addValue("lostReason", "lost".equals(target) ? lostReason.trim() : null)
                        .addValue("id", id)
                        .addValue("version", expectedVersion)
        );
        if (updated == 0) {
            throw new BusinessException(409, "CONCURRENT_UPDATE", "Lead vừa được cập nhật");
        }
        outbox.publish("LEAD", id, "lead.stage-changed.v1", Map.of(
                "leadId", id,
                "from", stage,
                "to", target
        ));
        return get(id);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateLead(
            Long customerId,
            String name,
            String source,
            String contactName,
            String contactPhone,
            String contactEmail,
            String companyName,
            String taxCode,
            String projectUrl,
            String requirement,
            short score,
            String temperature,
            BigDecimal estimatedValue,
            LocalDate expectedCloseDate,
            UUID assignedTo,
            OffsetDateTime nextFollowUpAt,
            String originRefType,
            String originRefId,
            String originPayload,
            String notes,
            List<LeadItem> items
    ) {
    }

    public record LeadItem(
            Long productId,
            String productSku,
            String productName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            String notes
    ) {
    }
}
