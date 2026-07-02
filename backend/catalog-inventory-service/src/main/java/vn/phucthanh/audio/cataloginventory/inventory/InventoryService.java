package vn.phucthanh.audio.cataloginventory.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phucthanh.audio.shared.domain.BusinessCodes;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.shared.web.BusinessException;

@Service
public class InventoryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public InventoryService(NamedParameterJdbcTemplate jdbc, OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional
    public Map<String, Object> move(CreateMovement command) {
        Map<String, Object> product = lockProduct(command.productId());
        BigDecimal before = (BigDecimal) product.get("stock_quantity");
        BigDecimal minimum = (BigDecimal) product.get("minimum_stock");
        BigDecimal delta = "in".equals(command.direction()) ? command.quantity() : command.quantity().negate();
        BigDecimal after = before.add(delta);
        if (after.signum() < 0) {
            throw new BusinessException(409, "INSUFFICIENT_STOCK", "Tồn kho không đủ cho giao dịch");
        }

        String movementCode = BusinessCodes.next("KHO");
        Long movementId = jdbc.queryForObject(
                """
                insert into public.inventory_movements (
                    movement_code, product_id, contract_id, repair_request_id,
                    customer_asset_id, customer_id, performed_by_user_id,
                    warehouse_code, movement_type, movement_direction, quantity,
                    unit, stock_before, stock_after, unit_cost, unit_price,
                    source_type, source_ref, document_number, serial_number,
                    batch_number, status, notes
                ) values (
                    :code, :productId, :contractId, :repairRequestId,
                    :customerAssetId, :customerId, :performedBy,
                    :warehouse, :movementType, :direction, :quantity,
                    :unit, :before, :after, :unitCost, :unitPrice,
                    :sourceType, :sourceRef, :documentNumber, :serialNumber,
                    :batchNumber, 'posted', :notes
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", movementCode)
                        .addValue("productId", command.productId())
                        .addValue("contractId", command.contractId())
                        .addValue("repairRequestId", command.repairRequestId())
                        .addValue("customerAssetId", command.customerAssetId())
                        .addValue("customerId", command.customerId())
                        .addValue("performedBy", command.performedBy())
                        .addValue("warehouse", command.warehouseCode())
                        .addValue("movementType", command.movementType())
                        .addValue("direction", command.direction())
                        .addValue("quantity", command.quantity())
                        .addValue("unit", command.unit())
                        .addValue("before", before)
                        .addValue("after", after)
                        .addValue("unitCost", command.unitCost())
                        .addValue("unitPrice", command.unitPrice())
                        .addValue("sourceType", command.sourceType())
                        .addValue("sourceRef", command.sourceRef())
                        .addValue("documentNumber", command.documentNumber())
                        .addValue("serialNumber", command.serialNumber())
                        .addValue("batchNumber", command.batchNumber())
                        .addValue("notes", command.notes()),
                Long.class
        );

        jdbc.update(
                """
                update public.products
                set stock_quantity = :after, version = version + 1, updated_at = now()
                where id = :productId
                """,
                Map.of("after", after, "productId", command.productId())
        );

        Long alertId = null;
        if (after.compareTo(minimum) <= 0) {
            alertId = createAlertIfAbsent(product, movementId, after, minimum);
        }
        outbox.publish("INVENTORY", movementId, "inventory.movement-created.v1", Map.of(
                "movementId", movementId,
                "productId", command.productId(),
                "stockBefore", before,
                "stockAfter", after
        ));
        if (alertId != null) {
            outbox.publish("STOCK_ALERT", alertId, "stock.alert-created.v1", Map.of(
                    "alertId", alertId,
                    "productId", command.productId(),
                    "stockQuantity", after
            ));
        }
        return jdbc.queryForMap(
                "select * from public.inventory_movements where id = :id",
                Map.of("id", movementId)
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> movements(long productId, int limit) {
        return jdbc.queryForList(
                """
                select *
                from public.inventory_movements
                where product_id = :productId
                order by movement_at desc, id desc
                limit :limit
                """,
                Map.of("productId", productId, "limit", Math.max(1, Math.min(limit, 200)))
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> openAlerts() {
        return jdbc.queryForList(
                """
                select *
                from public.stock_alerts
                where status in ('open', 'sent', 'acknowledged', 'failed', 'snoozed')
                order by severity desc, triggered_at desc
                """,
                Map.of()
        );
    }

    private Map<String, Object> lockProduct(long productId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                select id, sku, name, unit, stock_quantity, minimum_stock
                from public.products
                where id = :id
                for update
                """,
                Map.of("id", productId)
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm " + productId);
        }
        return rows.get(0);
    }

    private Long createAlertIfAbsent(
            Map<String, Object> product,
            long movementId,
            BigDecimal stock,
            BigDecimal minimum
    ) {
        List<Long> ids = jdbc.queryForList(
                """
                insert into public.stock_alerts (
                    alert_code, product_id, inventory_movement_id, warehouse_code,
                    product_sku, product_name, unit, alert_type, severity,
                    stock_quantity, minimum_stock, title, message,
                    notification_channel, status
                ) values (
                    :code, :productId, :movementId, 'MAIN',
                    :sku, :name, :unit, :type, :severity,
                    :stock, :minimum, :title, :message,
                    'telegram', 'open'
                )
                on conflict do nothing
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", BusinessCodes.next("TK"))
                        .addValue("productId", product.get("id"))
                        .addValue("movementId", movementId)
                        .addValue("sku", product.get("sku"))
                        .addValue("name", product.get("name"))
                        .addValue("unit", product.get("unit"))
                        .addValue("type", stock.signum() == 0 ? "out_of_stock" : "low_stock")
                        .addValue("severity", stock.signum() == 0 ? "critical" : "high")
                        .addValue("stock", stock)
                        .addValue("minimum", minimum)
                        .addValue("title", "Cảnh báo tồn kho " + product.get("sku"))
                        .addValue("message", "Tồn hiện tại " + stock + ", tồn tối thiểu " + minimum),
                Long.class
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public record CreateMovement(
            long productId,
            Long contractId,
            Long repairRequestId,
            Long customerAssetId,
            Long customerId,
            UUID performedBy,
            String warehouseCode,
            String movementType,
            String direction,
            BigDecimal quantity,
            String unit,
            BigDecimal unitCost,
            BigDecimal unitPrice,
            String sourceType,
            String sourceRef,
            String documentNumber,
            String serialNumber,
            String batchNumber,
            String notes
    ) {
    }
}
