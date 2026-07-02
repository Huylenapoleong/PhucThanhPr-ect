package vn.phucthanh.audio.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phucthanh.audio.shared.domain.BusinessCodes;
import vn.phucthanh.audio.shared.event.OutboxPublisher;

@Service
public class KpiService {

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public KpiService(NamedParameterJdbcTemplate jdbc, OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate from, LocalDate to, String periodType) {
        return jdbc.queryForList(
                """
                select *
                from public.kpi_snapshots
                where period_start >= :from
                  and period_end <= :to
                  and (:periodType is null or period_type = :periodType)
                  and report_status <> 'superseded'
                order by period_end desc, generated_at desc
                """,
                new MapSqlParameterSource()
                        .addValue("from", from)
                        .addValue("to", to)
                        .addValue("periodType", periodType)
        );
    }

    @Transactional
    public Map<String, Object> generate(LocalDate start, LocalDate end, String periodType) {
        MapSqlParameterSource period = new MapSqlParameterSource()
                .addValue("start", start)
                .addValue("endExclusive", end.plusDays(1));

        BigDecimal revenue = money("""
                select coalesce(sum(total_amount), 0)
                from public.invoices
                where invoice_status in ('issued', 'replaced')
                  and issue_date >= :start and issue_date < :endExclusive
                """, period);
        BigDecimal collected = money("""
                select coalesce(sum(signed_amount), 0)
                from public.payment_records
                where status = 'confirmed'
                  and paid_at >= :start and paid_at < :endExclusive
                """, period);
        BigDecimal pipeline = money("""
                select coalesce(sum(estimated_value), 0)
                from public.leads
                where stage not in ('closed', 'lost')
                """, period);
        BigDecimal quotationValue = money("""
                select coalesce(sum(total_amount), 0)
                from public.quotations
                where quotation_date >= :start and quotation_date < :endExclusive
                """, period);
        BigDecimal contractValue = money("""
                select coalesce(sum(total_value), 0)
                from public.contracts
                where created_at >= :start and created_at < :endExclusive
                """, period);
        BigDecimal receivables = money("""
                select coalesce(sum(remaining_amount), 0)
                from public.invoices
                where invoice_status in ('issued', 'replaced')
                  and payment_status in ('unpaid', 'partial', 'overdue')
                """, period);
        BigDecimal overdue = money("""
                select coalesce(sum(remaining_amount), 0)
                from public.invoices
                where due_date < current_date
                  and payment_status in ('unpaid', 'partial', 'overdue')
                """, period);

        int newLeads = count("select count(*) from public.leads where created_at >= :start and created_at < :endExclusive", period);
        int wonLeads = count("select count(*) from public.leads where stage in ('won','delivering','collecting','closed') and updated_at >= :start and updated_at < :endExclusive", period);
        int lostLeads = count("select count(*) from public.leads where stage = 'lost' and closed_at >= :start and closed_at < :endExclusive", period);
        int openLeads = count("select count(*) from public.leads where stage not in ('closed','lost')", period);
        int completedRepairs = count("select count(*) from public.repair_requests where completed_at >= :start and completed_at < :endExclusive", period);
        int openRepairs = count("select count(*) from public.repair_requests where status not in ('returned','cancelled')", period);
        int calls = count("select count(*) from public.call_logs where started_at >= :start and started_at < :endExclusive", period);
        int aiCalls = count("select count(*) from public.call_logs where result = 'ai_resolved' and started_at >= :start and started_at < :endExclusive", period);
        int transferredCalls = count("select count(*) from public.call_logs where result = 'transferred' and started_at >= :start and started_at < :endExclusive", period);
        int lowStock = count("select count(*) from public.products where stock_status = 'low_stock'", period);
        int outOfStock = count("select count(*) from public.products where stock_status = 'out_of_stock'", period);
        int activeAlerts = count("select count(*) from public.stock_alerts where status in ('open','sent','acknowledged','failed','snoozed')", period);

        BigDecimal conversion = newLeads == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(wonLeads * 100.0 / newLeads).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal averageDeal = wonLeads == 0
                ? BigDecimal.ZERO
                : contractValue.divide(BigDecimal.valueOf(wonLeads), 2, java.math.RoundingMode.HALF_UP);
        String code = BusinessCodes.next("KPI");

        Long id = jdbc.queryForObject(
                """
                insert into public.kpi_snapshots (
                    snapshot_code, period_type, period_start, period_end,
                    scope_type, currency, revenue, pipeline_value, quotation_value,
                    contract_value, receivables, overdue_amount, collected_amount,
                    new_leads, won_leads, lost_leads, open_leads, quoted_leads,
                    conversion_rate, avg_deal_value, completed_repairs, open_repairs,
                    overdue_repairs, call_count, ai_resolved_calls, transferred_calls,
                    low_stock_products, out_of_stock_products, active_stock_alerts,
                    report_status, notification_channel, source_payload
                ) values (
                    :code, :periodType, :start, :end, 'company', 'VND',
                    :revenue, :pipeline, :quotationValue, :contractValue,
                    :receivables, :overdue, :collected,
                    :newLeads, :wonLeads, :lostLeads, :openLeads, 0,
                    :conversion, :averageDeal, :completedRepairs, :openRepairs,
                    0, :calls, :aiCalls, :transferredCalls,
                    :lowStock, :outOfStock, :activeAlerts,
                    'generated', 'telegram', '{}'::jsonb
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("periodType", periodType)
                        .addValue("start", start)
                        .addValue("end", end)
                        .addValue("revenue", revenue)
                        .addValue("pipeline", pipeline)
                        .addValue("quotationValue", quotationValue)
                        .addValue("contractValue", contractValue)
                        .addValue("receivables", receivables)
                        .addValue("overdue", overdue)
                        .addValue("collected", collected)
                        .addValue("newLeads", newLeads)
                        .addValue("wonLeads", wonLeads)
                        .addValue("lostLeads", lostLeads)
                        .addValue("openLeads", openLeads)
                        .addValue("conversion", conversion)
                        .addValue("averageDeal", averageDeal)
                        .addValue("completedRepairs", completedRepairs)
                        .addValue("openRepairs", openRepairs)
                        .addValue("calls", calls)
                        .addValue("aiCalls", aiCalls)
                        .addValue("transferredCalls", transferredCalls)
                        .addValue("lowStock", lowStock)
                        .addValue("outOfStock", outOfStock)
                        .addValue("activeAlerts", activeAlerts),
                Long.class
        );
        outbox.publish("KPI", id, "kpi.snapshot-generated.v1", Map.of("snapshotId", id, "snapshotCode", code));
        return jdbc.queryForMap(
                "select * from public.kpi_snapshots where id = :id",
                Map.of("id", id)
        );
    }

    private BigDecimal money(String sql, MapSqlParameterSource parameters) {
        BigDecimal value = jdbc.queryForObject(sql, parameters, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private int count(String sql, MapSqlParameterSource parameters) {
        Integer value = jdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }
}
