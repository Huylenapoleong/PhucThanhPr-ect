package vn.phucthanh.audio.cataloginventory.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping("/inventory/movements")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> move(@Valid @RequestBody CreateMovementRequest request) {
        return service.move(request.toCommand());
    }

    @GetMapping("/inventory/movements")
    List<Map<String, Object>> movements(
            @RequestParam long productId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return service.movements(productId, limit);
    }

    @GetMapping("/stock-alerts")
    List<Map<String, Object>> alerts() {
        return service.openAlerts();
    }

    public record CreateMovementRequest(
            long productId,
            Long contractId,
            Long repairRequestId,
            Long customerAssetId,
            Long customerId,
            UUID performedBy,
            String warehouseCode,
            @NotNull String movementType,
            @Pattern(regexp = "in|out") String direction,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @Pattern(regexp = "piece|set|box|meter") String unit,
            @NotNull @DecimalMin("0") BigDecimal unitCost,
            @NotNull @DecimalMin("0") BigDecimal unitPrice,
            String sourceType,
            String sourceRef,
            String documentNumber,
            String serialNumber,
            String batchNumber,
            String notes
    ) {
        InventoryService.CreateMovement toCommand() {
            return new InventoryService.CreateMovement(
                    productId,
                    contractId,
                    repairRequestId,
                    customerAssetId,
                    customerId,
                    performedBy,
                    warehouseCode == null ? "MAIN" : warehouseCode.trim().toUpperCase(),
                    movementType,
                    direction,
                    quantity,
                    unit == null ? "piece" : unit,
                    unitCost,
                    unitPrice,
                    sourceType == null ? "manual" : sourceType,
                    sourceRef,
                    documentNumber,
                    serialNumber,
                    batchNumber,
                    notes
            );
        }
    }
}
