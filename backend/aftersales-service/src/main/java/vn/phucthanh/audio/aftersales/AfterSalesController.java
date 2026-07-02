package vn.phucthanh.audio.aftersales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AfterSalesController {

    private final AfterSalesService service;

    public AfterSalesController(AfterSalesService service) {
        this.service = service;
    }

    @PostMapping("/customer-assets")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createAsset(@Valid @RequestBody AssetRequest request) {
        return service.createAsset(new AfterSalesService.CreateAsset(
                request.customerId(), request.productId(), request.contractId(),
                request.productSku(), request.productName(), request.serialNumber(),
                request.purchaseDate(), request.deliveredAt(), request.warrantyMonths(),
                request.warrantyStartsOn(), request.warrantyExpiresOn(),
                request.installationAddress(), request.notes()
        ));
    }

    @GetMapping("/customer-assets")
    List<Map<String, Object>> assets(@RequestParam long customerId) {
        return service.assets(customerId);
    }

    @PostMapping("/repairs")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createRepair(@Valid @RequestBody RepairRequest request) {
        return service.createRepair(new AfterSalesService.CreateRepair(
                request.customerId(), request.customerAssetId(), request.productId(),
                request.reportedProductName(), request.reportedSerialNumber(),
                request.requestType(), request.requestChannel(), request.priority(),
                request.contactName(), request.contactPhone(), request.contactEmail(),
                request.issueDescription(), request.intakeNotes(), request.technicianId(),
                request.expectedReturnAt()
        ));
    }

    @GetMapping("/repairs")
    List<Map<String, Object>> repairs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID technicianId
    ) {
        return service.repairs(status, technicianId);
    }

    @PatchMapping("/repairs/{id}/status")
    Map<String, Object> transition(
            @org.springframework.web.bind.annotation.PathVariable long id,
            @Valid @RequestBody RepairTransitionRequest request
    ) {
        return service.transitionRepair(
                id, request.status(), request.resolution(), request.partsCost(),
                request.laborCost(), request.outsourcedCost(), request.version()
        );
    }

    @PostMapping("/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> reminder(@Valid @RequestBody ReminderRequest request) {
        return service.createReminder(new AfterSalesService.CreateReminder(
                request.customerId(), request.leadId(), request.contractId(), request.invoiceId(),
                request.customerAssetId(), request.repairRequestId(), request.reminderType(),
                request.title(), request.message(), request.dueAt(), request.assignedTo(),
                request.priority(), request.channel(), request.recipientName(),
                request.recipientPhone(), request.recipientEmail()
        ));
    }

    @GetMapping("/reminders/due")
    List<Map<String, Object>> due(@RequestParam OffsetDateTime before) {
        return service.dueReminders(before);
    }

    public record AssetRequest(
            long customerId, long productId, Long contractId, String productSku,
            @NotBlank String productName, String serialNumber, LocalDate purchaseDate,
            OffsetDateTime deliveredAt, @Min(0) @Max(240) short warrantyMonths,
            LocalDate warrantyStartsOn, LocalDate warrantyExpiresOn,
            String installationAddress, String notes
    ) {
    }

    public record RepairRequest(
            long customerId, Long customerAssetId, Long productId, String reportedProductName,
            String reportedSerialNumber,
            @Pattern(regexp = "warranty|paid_repair|outsourced") String requestType,
            @Pattern(regexp = "web|zalo|facebook|call|manual|other") String requestChannel,
            @Pattern(regexp = "low|normal|high|urgent") String priority,
            String contactName, String contactPhone, String contactEmail,
            @NotBlank String issueDescription, String intakeNotes, UUID technicianId,
            OffsetDateTime expectedReturnAt
    ) {
    }

    public record RepairTransitionRequest(
            @Pattern(regexp = "assigned|processing|waiting_parts|waiting_customer|completed|notified|returned|cancelled")
            String status,
            String resolution,
            @DecimalMin("0") BigDecimal partsCost,
            @DecimalMin("0") BigDecimal laborCost,
            @DecimalMin("0") BigDecimal outsourcedCost,
            @PositiveOrZero long version
    ) {
    }

    public record ReminderRequest(
            long customerId, Long leadId, Long contractId, Long invoiceId,
            Long customerAssetId, Long repairRequestId,
            @Pattern(regexp = "debt|warranty|event|birthday|follow_up|repair_completed|contract_renewal|payment_due")
            String reminderType,
            @NotBlank String title, String message, @NotNull OffsetDateTime dueAt,
            UUID assignedTo, @Pattern(regexp = "low|normal|high|urgent") String priority,
            @Pattern(regexp = "telegram|zalo|facebook|email|call|sms|manual") String channel,
            String recipientName, String recipientPhone, String recipientEmail
    ) {
    }
}
