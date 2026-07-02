package vn.phucthanh.audio.commerce.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phucthanh.audio.shared.domain.BusinessCodes;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.shared.web.BusinessException;

@Service
public class CommerceDocumentService {

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public CommerceDocumentService(NamedParameterJdbcTemplate jdbc, OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional
    public Map<String, Object> createQuotation(CreateQuotation command) {
        Totals totals = totals(command.items(), command.headerDiscount());
        String code = BusinessCodes.next("BG");
        Long id = jdbc.queryForObject(
                """
                insert into public.quotations (
                    quotation_code, customer_id, lead_id, quotation_date, valid_until,
                    subtotal, discount_amount, tax_amount, currency, status,
                    payment_terms, delivery_terms, warranty_terms, notes, created_by_user_id
                ) values (
                    :code, :customerId, :leadId, current_date, :validUntil,
                    :subtotal, :discount, :tax, 'VND', 'draft',
                    :paymentTerms, :deliveryTerms, :warrantyTerms, :notes, :createdBy
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("customerId", command.customerId())
                        .addValue("leadId", command.leadId())
                        .addValue("validUntil", command.validUntil())
                        .addValue("subtotal", totals.subtotal())
                        .addValue("discount", command.headerDiscount())
                        .addValue("tax", totals.tax())
                        .addValue("paymentTerms", command.paymentTerms())
                        .addValue("deliveryTerms", command.deliveryTerms())
                        .addValue("warrantyTerms", command.warrantyTerms())
                        .addValue("notes", command.notes())
                        .addValue("createdBy", command.createdBy()),
                Long.class
        );
        int sort = 0;
        for (DocumentItem item : command.items()) {
            jdbc.update(
                    """
                    insert into public.quotation_items (
                        quotation_id, product_id, product_sku, product_name, unit,
                        quantity, unit_price, cost_price, discount_amount, tax_rate,
                        notes, sort_order
                    ) values (
                        :headerId, :productId, :sku, :name, :unit,
                        :quantity, :unitPrice, :costPrice, :discount, :taxRate,
                        :notes, :sortOrder
                    )
                    """,
                    itemParameters(id, item, sort++)
            );
        }
        outbox.publish("QUOTATION", id, "quotation.created.v1", Map.of(
                "quotationId", id,
                "quotationCode", code,
                "totalAmount", totals.subtotal().subtract(command.headerDiscount()).add(totals.tax())
        ));
        return getHeader("quotations", "quotation_items", "quotation_id", id);
    }

    @Transactional
    public Map<String, Object> approveQuotation(long id, long expectedVersion) {
        int updated = jdbc.update(
                """
                update public.quotations
                set status = 'approved', approved_at = now(), sent_at = coalesce(sent_at, now()),
                    version = version + 1, updated_at = now()
                where id = :id and version = :version and status in ('draft', 'sent')
                """,
                Map.of("id", id, "version", expectedVersion)
        );
        if (updated == 0) {
            throw new BusinessException(409, "QUOTATION_NOT_APPROVABLE", "Báo giá không thể duyệt hoặc đã thay đổi");
        }
        outbox.publish("QUOTATION", id, "quotation.approved.v1", Map.of("quotationId", id));
        outbox.publish("QUOTATION", id, "quotation.document-requested.v1", Map.of("quotationId", id));
        return getHeader("quotations", "quotation_items", "quotation_id", id);
    }

    @Transactional
    public Map<String, Object> createContract(CreateContract command) {
        Map<String, Object> quotation = requiredRow(
                "select customer_id, total_amount, status from public.quotations where id = :id",
                command.quotationId(),
                "QUOTATION_NOT_FOUND"
        );
        if (!"approved".equals(quotation.get("status"))) {
            throw new BusinessException(409, "QUOTATION_NOT_APPROVED", "Chỉ tạo hợp đồng từ báo giá đã duyệt");
        }
        String code = BusinessCodes.next("HD");
        Long id = jdbc.queryForObject(
                """
                insert into public.contracts (
                    contract_code, customer_id, quotation_id, signed_date,
                    start_date, end_date, payment_due_date, total_value,
                    paid_amount, payment_status, status, notes, created_by_user_id
                ) values (
                    :code, :customerId, :quotationId, :signedDate,
                    :startDate, :endDate, :paymentDueDate, :totalValue,
                    0, 'unpaid', :status, :notes, :createdBy
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("customerId", quotation.get("customer_id"))
                        .addValue("quotationId", command.quotationId())
                        .addValue("signedDate", command.signedDate())
                        .addValue("startDate", command.startDate())
                        .addValue("endDate", command.endDate())
                        .addValue("paymentDueDate", command.paymentDueDate())
                        .addValue("totalValue", quotation.get("total_amount"))
                        .addValue("status", command.signedDate() == null ? "draft" : "active")
                        .addValue("notes", command.notes())
                        .addValue("createdBy", command.createdBy()),
                Long.class
        );
        outbox.publish("CONTRACT", id, "contract.created.v1", Map.of("contractId", id, "contractCode", code));
        outbox.publish("CONTRACT", id, "contract.document-requested.v1", Map.of("contractId", id));
        return requiredRow("select * from public.contracts where id = :id", id, "CONTRACT_NOT_FOUND");
    }

    @Transactional
    public Map<String, Object> createInvoice(CreateInvoice command) {
        Totals totals = totals(command.items(), command.headerDiscount());
        String code = BusinessCodes.next("HĐ");
        Long id = jdbc.queryForObject(
                """
                insert into public.invoices (
                    invoice_code, customer_id, contract_id, quotation_id, invoice_type,
                    buyer_name, buyer_tax_code, buyer_address, buyer_email,
                    issue_date, due_date, currency, subtotal, discount_amount,
                    tax_amount, paid_amount, invoice_status, payment_status,
                    created_by_user_id, notes
                ) values (
                    :code, :customerId, :contractId, :quotationId, 'sale',
                    :buyerName, :buyerTaxCode, :buyerAddress, :buyerEmail,
                    :issueDate, :dueDate, 'VND', :subtotal, :discount,
                    :tax, 0, 'draft', 'unpaid', :createdBy, :notes
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("customerId", command.customerId())
                        .addValue("contractId", command.contractId())
                        .addValue("quotationId", command.quotationId())
                        .addValue("buyerName", command.buyerName())
                        .addValue("buyerTaxCode", command.buyerTaxCode())
                        .addValue("buyerAddress", command.buyerAddress())
                        .addValue("buyerEmail", command.buyerEmail())
                        .addValue("issueDate", command.issueDate())
                        .addValue("dueDate", command.dueDate())
                        .addValue("subtotal", totals.subtotal())
                        .addValue("discount", command.headerDiscount())
                        .addValue("tax", totals.tax())
                        .addValue("createdBy", command.createdBy())
                        .addValue("notes", command.notes()),
                Long.class
        );
        int sort = 0;
        for (DocumentItem item : command.items()) {
            MapSqlParameterSource parameters = itemParameters(id, item, sort++);
            parameters.addValue("quotationItemId", item.sourceItemId());
            jdbc.update(
                    """
                    insert into public.invoice_items (
                        invoice_id, product_id, quotation_item_id, product_sku,
                        product_name, unit, item_type, quantity, unit_price,
                        discount_amount, tax_rate, notes, sort_order
                    ) values (
                        :headerId, :productId, :quotationItemId, :sku,
                        :name, :unit, 'sale', :quantity, :unitPrice,
                        :discount, :taxRate, :notes, :sortOrder
                    )
                    """,
                    parameters
            );
        }
        outbox.publish("INVOICE", id, "invoice.created.v1", Map.of("invoiceId", id, "invoiceCode", code));
        return getHeader("invoices", "invoice_items", "invoice_id", id);
    }

    @Transactional
    public Map<String, Object> recordPayment(RecordPayment command) {
        if (command.invoiceId() == null && command.contractId() == null) {
            throw new BusinessException(400, "PAYMENT_TARGET_REQUIRED", "Thanh toán phải gắn với hóa đơn hoặc hợp đồng");
        }
        if (command.idempotencyKey() != null) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "select * from public.payment_records where idempotency_key = :key",
                    Map.of("key", command.idempotencyKey())
            );
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
        }
        String code = BusinessCodes.next("TT");
        Long id;
        try {
            id = jdbc.queryForObject(
                    """
                    insert into public.payment_records (
                        payment_code, customer_id, contract_id, invoice_id,
                        direction, amount, currency, payment_method, source,
                        status, paid_at, confirmed_at, provider,
                        external_transaction_id, idempotency_key, bank_code,
                        payer_name, reference_text, recorded_by_user_id,
                        source_payload, notes
                    ) values (
                        :code, :customerId, :contractId, :invoiceId,
                        'receipt', :amount, 'VND', :method, :source,
                        'confirmed', :paidAt, now(), :provider,
                        :externalTransactionId, :idempotencyKey, :bankCode,
                        :payerName, :referenceText, :recordedBy,
                        cast(:sourcePayload as jsonb), :notes
                    )
                    returning id
                    """,
                    new MapSqlParameterSource()
                            .addValue("code", code)
                            .addValue("customerId", command.customerId())
                            .addValue("contractId", command.contractId())
                            .addValue("invoiceId", command.invoiceId())
                            .addValue("amount", command.amount())
                            .addValue("method", command.paymentMethod())
                            .addValue("source", command.source())
                            .addValue("paidAt", command.paidAt())
                            .addValue("provider", command.provider())
                            .addValue("externalTransactionId", command.externalTransactionId())
                            .addValue("idempotencyKey", command.idempotencyKey())
                            .addValue("bankCode", command.bankCode())
                            .addValue("payerName", command.payerName())
                            .addValue("referenceText", command.referenceText())
                            .addValue("recordedBy", command.recordedBy())
                            .addValue("sourcePayload", command.sourcePayload())
                            .addValue("notes", command.notes()),
                    Long.class
            );
        } catch (DataIntegrityViolationException exception) {
            if (command.idempotencyKey() == null) {
                throw exception;
            }
            return jdbc.queryForMap(
                    "select * from public.payment_records where idempotency_key = :key",
                    Map.of("key", command.idempotencyKey())
            );
        }
        updateReceivables(command.invoiceId(), command.contractId());
        outbox.publish("PAYMENT", id, "payment.recorded.v1", Map.of(
                "paymentId", id,
                "invoiceId", command.invoiceId() == null ? "" : command.invoiceId(),
                "contractId", command.contractId() == null ? "" : command.contractId(),
                "amount", command.amount()
        ));
        return requiredRow("select * from public.payment_records where id = :id", id, "PAYMENT_NOT_FOUND");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getQuotation(long id) {
        return getHeader("quotations", "quotation_items", "quotation_id", id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInvoice(long id) {
        return getHeader("invoices", "invoice_items", "invoice_id", id);
    }

    private void updateReceivables(Long invoiceId, Long contractId) {
        if (invoiceId != null) {
            jdbc.update(
                    """
                    update public.invoices i
                    set paid_amount = least(i.total_amount, coalesce((
                            select sum(p.signed_amount)
                            from public.payment_records p
                            where p.invoice_id = i.id and p.status = 'confirmed'
                        ), 0)),
                        payment_status = case
                            when coalesce((select sum(p.signed_amount)
                                           from public.payment_records p
                                           where p.invoice_id = i.id and p.status = 'confirmed'), 0) >= i.total_amount
                                then 'paid'
                            when coalesce((select sum(p.signed_amount)
                                           from public.payment_records p
                                           where p.invoice_id = i.id and p.status = 'confirmed'), 0) > 0
                                then 'partial'
                            else 'unpaid'
                        end,
                        version = version + 1,
                        updated_at = now()
                    where i.id = :id
                    """,
                    Map.of("id", invoiceId)
            );
        }
        if (contractId != null) {
            jdbc.update(
                    """
                    update public.contracts c
                    set paid_amount = least(c.total_value, coalesce((
                            select sum(p.signed_amount)
                            from public.payment_records p
                            where p.contract_id = c.id and p.status = 'confirmed'
                        ), 0)),
                        payment_status = case
                            when coalesce((select sum(p.signed_amount)
                                           from public.payment_records p
                                           where p.contract_id = c.id and p.status = 'confirmed'), 0) >= c.total_value
                                then 'paid'
                            when coalesce((select sum(p.signed_amount)
                                           from public.payment_records p
                                           where p.contract_id = c.id and p.status = 'confirmed'), 0) > 0
                                then 'partial'
                            else 'unpaid'
                        end,
                        version = version + 1,
                        updated_at = now()
                    where c.id = :id
                    """,
                    Map.of("id", contractId)
            );
        }
    }

    private MapSqlParameterSource itemParameters(long headerId, DocumentItem item, int sortOrder) {
        return new MapSqlParameterSource()
                .addValue("headerId", headerId)
                .addValue("productId", item.productId())
                .addValue("sku", item.productSku())
                .addValue("name", item.productName())
                .addValue("unit", item.unit())
                .addValue("quantity", item.quantity())
                .addValue("unitPrice", item.unitPrice())
                .addValue("costPrice", item.costPrice())
                .addValue("discount", item.discountAmount())
                .addValue("taxRate", item.taxRate())
                .addValue("notes", item.notes())
                .addValue("sortOrder", sortOrder);
    }

    private Totals totals(List<DocumentItem> items, BigDecimal headerDiscount) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(400, "ITEMS_REQUIRED", "Chứng từ cần ít nhất một dòng hàng");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (DocumentItem item : items) {
            BigDecimal base = item.quantity().multiply(item.unitPrice()).subtract(item.discountAmount());
            if (base.signum() < 0) {
                throw new BusinessException(400, "INVALID_ITEM_TOTAL", "Giảm giá dòng hàng vượt giá trị dòng");
            }
            subtotal = subtotal.add(base);
            tax = tax.add(base.multiply(item.taxRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        tax = tax.setScale(2, RoundingMode.HALF_UP);
        if (headerDiscount.compareTo(subtotal) > 0) {
            throw new BusinessException(400, "INVALID_DISCOUNT", "Giảm giá chứng từ vượt tổng tiền hàng");
        }
        return new Totals(subtotal, tax);
    }

    private Map<String, Object> getHeader(String table, String itemTable, String foreignKey, long id) {
        if (!Set.of("quotations", "invoices").contains(table)) {
            throw new IllegalArgumentException("Unsupported document table");
        }
        Map<String, Object> header = requiredRow(
                "select * from public." + table + " where id = :id",
                id,
                "DOCUMENT_NOT_FOUND"
        );
        Map<String, Object> result = new LinkedHashMap<>(header);
        result.put("items", jdbc.queryForList(
                "select * from public." + itemTable + " where " + foreignKey + " = :id order by sort_order, id",
                Map.of("id", id)
        ));
        return result;
    }

    private Map<String, Object> requiredRow(String sql, long id, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of("id", id));
        if (rows.isEmpty()) {
            throw new BusinessException(404, code, "Không tìm thấy dữ liệu " + id);
        }
        return rows.get(0);
    }

    private record Totals(BigDecimal subtotal, BigDecimal tax) {
    }

    public record DocumentItem(
            Long sourceItemId,
            Long productId,
            String productSku,
            String productName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal costPrice,
            BigDecimal discountAmount,
            BigDecimal taxRate,
            String notes
    ) {
    }

    public record CreateQuotation(
            long customerId,
            Long leadId,
            LocalDate validUntil,
            BigDecimal headerDiscount,
            String paymentTerms,
            String deliveryTerms,
            String warrantyTerms,
            String notes,
            UUID createdBy,
            List<DocumentItem> items
    ) {
    }

    public record CreateContract(
            long quotationId,
            LocalDate signedDate,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate paymentDueDate,
            String notes,
            UUID createdBy
    ) {
    }

    public record CreateInvoice(
            long customerId,
            Long contractId,
            Long quotationId,
            String buyerName,
            String buyerTaxCode,
            String buyerAddress,
            String buyerEmail,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal headerDiscount,
            String notes,
            UUID createdBy,
            List<DocumentItem> items
    ) {
    }

    public record RecordPayment(
            long customerId,
            Long contractId,
            Long invoiceId,
            BigDecimal amount,
            String paymentMethod,
            String source,
            OffsetDateTime paidAt,
            String provider,
            String externalTransactionId,
            String idempotencyKey,
            String bankCode,
            String payerName,
            String referenceText,
            UUID recordedBy,
            String sourcePayload,
            String notes
    ) {
    }
}
