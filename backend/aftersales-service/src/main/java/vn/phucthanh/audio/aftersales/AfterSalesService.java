package vn.phucthanh.audio.aftersales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
public class AfterSalesService {

    private static final Map<String, Set<String>> REPAIR_TRANSITIONS = Map.of(
            "received", Set.of("assigned", "processing", "cancelled"),
            "assigned", Set.of("processing", "waiting_parts", "waiting_customer", "cancelled"),
            "processing", Set.of("waiting_parts", "waiting_customer", "completed", "cancelled"),
            "waiting_parts", Set.of("processing", "completed", "cancelled"),
            "waiting_customer", Set.of("processing", "completed", "cancelled"),
            "completed", Set.of("notified", "returned"),
            "notified", Set.of("returned")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public AfterSalesService(NamedParameterJdbcTemplate jdbc, OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional
    public Map<String, Object> createAsset(CreateAsset command) {
        String code = BusinessCodes.next("TB");
        Long id = jdbc.queryForObject(
                """
                insert into public.customer_assets (
                    asset_code, customer_id, product_id, contract_id, product_sku,
                    product_name, serial_number, purchase_date, delivered_at,
                    warranty_months, warranty_starts_on, warranty_expires_on,
                    installation_address, status, notes
                ) values (
                    :code, :customerId, :productId, :contractId, :sku,
                    :name, :serial, :purchaseDate, :deliveredAt,
                    :warrantyMonths, :warrantyStarts, :warrantyExpires,
                    :installationAddress, 'active', :notes
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("customerId", command.customerId())
                        .addValue("productId", command.productId())
                        .addValue("contractId", command.contractId())
                        .addValue("sku", command.productSku())
                        .addValue("name", command.productName())
                        .addValue("serial", command.serialNumber())
                        .addValue("purchaseDate", command.purchaseDate())
                        .addValue("deliveredAt", command.deliveredAt())
                        .addValue("warrantyMonths", command.warrantyMonths())
                        .addValue("warrantyStarts", command.warrantyStartsOn())
                        .addValue("warrantyExpires", command.warrantyExpiresOn())
                        .addValue("installationAddress", command.installationAddress())
                        .addValue("notes", command.notes()),
                Long.class
        );
        outbox.publish("CUSTOMER_ASSET", id, "customer-asset.created.v1", Map.of("assetId", id, "assetCode", code));
        return row("customer_assets", id, "ASSET_NOT_FOUND");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> assets(long customerId) {
        return jdbc.queryForList(
                """
                select * from public.customer_assets
                where customer_id = :customerId
                order by delivered_at desc nulls last, id desc
                """,
                Map.of("customerId", customerId)
        );
    }

    @Transactional
    public Map<String, Object> createRepair(CreateRepair command) {
        if (command.customerAssetId() == null
                && command.productId() == null
                && (command.reportedProductName() == null || command.reportedProductName().isBlank())) {
            throw new BusinessException(400, "PRODUCT_REQUIRED", "Cần xác định thiết bị hoặc tên sản phẩm báo hỏng");
        }
        String code = BusinessCodes.next("SC");
        Long id = jdbc.queryForObject(
                """
                insert into public.repair_requests (
                    repair_code, customer_id, customer_asset_id, product_id,
                    reported_product_name, reported_serial_number, request_type,
                    request_channel, priority, contact_name, contact_phone,
                    contact_email, issue_description, intake_notes,
                    warranty_decision, technician_user_id, expected_return_at,
                    status, parts_cost, labor_cost, outsourced_cost,
                    notification_channel, notification_status
                ) values (
                    :code, :customerId, :assetId, :productId,
                    :reportedName, :reportedSerial, :requestType,
                    :channel, :priority, :contactName, :contactPhone,
                    :contactEmail, :issue, :intakeNotes,
                    'pending', :technicianId, :expectedReturnAt,
                    :status, 0, 0, 0, 'none', 'not_required'
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("customerId", command.customerId())
                        .addValue("assetId", command.customerAssetId())
                        .addValue("productId", command.productId())
                        .addValue("reportedName", command.reportedProductName())
                        .addValue("reportedSerial", command.reportedSerialNumber())
                        .addValue("requestType", command.requestType())
                        .addValue("channel", command.requestChannel())
                        .addValue("priority", command.priority())
                        .addValue("contactName", command.contactName())
                        .addValue("contactPhone", command.contactPhone())
                        .addValue("contactEmail", command.contactEmail())
                        .addValue("issue", command.issueDescription())
                        .addValue("intakeNotes", command.intakeNotes())
                        .addValue("technicianId", command.technicianId())
                        .addValue("expectedReturnAt", command.expectedReturnAt())
                        .addValue("status", command.technicianId() == null ? "received" : "assigned"),
                Long.class
        );
        outbox.publish("REPAIR", id, "repair.created.v1", Map.of("repairId", id, "repairCode", code));
        return row("repair_requests", id, "REPAIR_NOT_FOUND");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> repairs(String status, UUID technicianId) {
        return jdbc.queryForList(
                """
                select *
                from public.repair_requests
                where (:status is null or status = :status)
                  and (:technicianId is null or technician_user_id = :technicianId)
                order by priority desc, received_at desc
                """,
                new MapSqlParameterSource()
                        .addValue("status", blankToNull(status))
                        .addValue("technicianId", technicianId)
        );
    }

    @Transactional
    public Map<String, Object> transitionRepair(
            long id,
            String target,
            String resolution,
            BigDecimal partsCost,
            BigDecimal laborCost,
            BigDecimal outsourcedCost,
            long expectedVersion
    ) {
        Map<String, Object> current = row("repair_requests", id, "REPAIR_NOT_FOUND");
        String from = (String) current.get("status");
        if (!REPAIR_TRANSITIONS.getOrDefault(from, Set.of()).contains(target)) {
            throw new BusinessException(409, "INVALID_REPAIR_TRANSITION", "Không thể chuyển phiếu từ " + from + " sang " + target);
        }
        if ("completed".equals(target) && (resolution == null || resolution.isBlank())) {
            throw new BusinessException(400, "RESOLUTION_REQUIRED", "Cần nhập kết quả sửa chữa");
        }
        int updated = jdbc.update(
                """
                update public.repair_requests
                set status = :target,
                    resolution = coalesce(:resolution, resolution),
                    parts_cost = coalesce(:partsCost, parts_cost),
                    labor_cost = coalesce(:laborCost, labor_cost),
                    outsourced_cost = coalesce(:outsourcedCost, outsourced_cost),
                    processing_started_at = case when :target = 'processing'
                        then coalesce(processing_started_at, now()) else processing_started_at end,
                    completed_at = case when :target = 'completed' then now() else completed_at end,
                    returned_at = case when :target = 'returned' then now() else returned_at end,
                    cancelled_at = case when :target = 'cancelled' then now() else cancelled_at end,
                    version = version + 1,
                    updated_at = now()
                where id = :id and version = :version
                """,
                new MapSqlParameterSource()
                        .addValue("target", target)
                        .addValue("resolution", blankToNull(resolution))
                        .addValue("partsCost", partsCost)
                        .addValue("laborCost", laborCost)
                        .addValue("outsourcedCost", outsourcedCost)
                        .addValue("id", id)
                        .addValue("version", expectedVersion)
        );
        if (updated == 0) {
            throw new BusinessException(409, "CONCURRENT_UPDATE", "Phiếu sửa chữa vừa được cập nhật");
        }
        outbox.publish("REPAIR", id, "repair.status-changed.v1", Map.of("repairId", id, "from", from, "to", target));
        return row("repair_requests", id, "REPAIR_NOT_FOUND");
    }

    @Transactional
    public Map<String, Object> createReminder(CreateReminder command) {
        String code = BusinessCodes.next("NH");
        Long id = jdbc.queryForObject(
                """
                insert into public.customer_reminders (
                    reminder_code, customer_id, lead_id, contract_id, invoice_id,
                    customer_asset_id, repair_request_id, reminder_type, title,
                    message, due_at, assigned_to_user_id, priority, channel,
                    recipient_name, recipient_phone, recipient_email, status
                ) values (
                    :code, :customerId, :leadId, :contractId, :invoiceId,
                    :assetId, :repairId, :type, :title,
                    :message, :dueAt, :assignedTo, :priority, :channel,
                    :recipientName, :recipientPhone, :recipientEmail, 'pending'
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("customerId", command.customerId())
                        .addValue("leadId", command.leadId())
                        .addValue("contractId", command.contractId())
                        .addValue("invoiceId", command.invoiceId())
                        .addValue("assetId", command.customerAssetId())
                        .addValue("repairId", command.repairRequestId())
                        .addValue("type", command.reminderType())
                        .addValue("title", command.title())
                        .addValue("message", command.message())
                        .addValue("dueAt", command.dueAt())
                        .addValue("assignedTo", command.assignedTo())
                        .addValue("priority", command.priority())
                        .addValue("channel", command.channel())
                        .addValue("recipientName", command.recipientName())
                        .addValue("recipientPhone", command.recipientPhone())
                        .addValue("recipientEmail", command.recipientEmail()),
                Long.class
        );
        outbox.publish("REMINDER", id, "customer.reminder-created.v1", Map.of("reminderId", id));
        return row("customer_reminders", id, "REMINDER_NOT_FOUND");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> dueReminders(OffsetDateTime before) {
        return jdbc.queryForList(
                """
                select * from public.customer_reminders
                where status in ('pending', 'failed')
                  and due_at <= :before
                  and (next_retry_at is null or next_retry_at <= now())
                order by priority desc, due_at
                """,
                Map.of("before", before)
        );
    }

    private Map<String, Object> row(String table, long id, String code) {
        if (!Set.of("customer_assets", "repair_requests", "customer_reminders").contains(table)) {
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateAsset(
            long customerId, long productId, Long contractId, String productSku,
            String productName, String serialNumber, LocalDate purchaseDate,
            OffsetDateTime deliveredAt, short warrantyMonths, LocalDate warrantyStartsOn,
            LocalDate warrantyExpiresOn, String installationAddress, String notes
    ) {
    }

    public record CreateRepair(
            long customerId, Long customerAssetId, Long productId, String reportedProductName,
            String reportedSerialNumber, String requestType, String requestChannel,
            String priority, String contactName, String contactPhone, String contactEmail,
            String issueDescription, String intakeNotes, UUID technicianId,
            OffsetDateTime expectedReturnAt
    ) {
    }

    public record CreateReminder(
            long customerId, Long leadId, Long contractId, Long invoiceId,
            Long customerAssetId, Long repairRequestId, String reminderType,
            String title, String message, OffsetDateTime dueAt, UUID assignedTo,
            String priority, String channel, String recipientName,
            String recipientPhone, String recipientEmail
    ) {
    }
}
