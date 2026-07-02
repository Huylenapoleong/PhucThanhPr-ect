package vn.phucthanh.audio.commerce.lead;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/leads")
public class LeadController {

    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @GetMapping
    List<Map<String, Object>> list(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(stage, assignedTo, page, size);
    }

    @GetMapping("/{id}")
    Map<String, Object> get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> create(@Valid @RequestBody CreateLeadRequest request) {
        return service.create(request.toCommand());
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAnyRole('SALES','ADMIN')")
    Map<String, Object> transition(
            @PathVariable long id,
            @Valid @RequestBody TransitionRequest request
    ) {
        return service.transition(id, request.stage(), request.lostReason(), request.version());
    }

    public record CreateLeadRequest(
            Long customerId,
            @NotBlank String name,
            @Pattern(regexp = "web|web_cart|facebook|zalo|call|dauthau|referral|manual|other") String source,
            String contactName,
            String contactPhone,
            String contactEmail,
            String companyName,
            String taxCode,
            String projectUrl,
            String requirement,
            @Min(0) @Max(100) short score,
            @Pattern(regexp = "hot|warm|cold") String temperature,
            @NotNull @DecimalMin("0") BigDecimal estimatedValue,
            LocalDate expectedCloseDate,
            UUID assignedTo,
            OffsetDateTime nextFollowUpAt,
            @Pattern(regexp = "cart|call|message|tender|manual|other") String originRefType,
            String originRefId,
            String originPayload,
            String notes,
            List<@Valid LeadItemRequest> items
    ) {
        LeadService.CreateLead toCommand() {
            return new LeadService.CreateLead(
                    customerId,
                    name,
                    source == null ? "manual" : source,
                    contactName,
                    contactPhone,
                    contactEmail,
                    companyName,
                    taxCode,
                    projectUrl,
                    requirement,
                    score,
                    temperature == null ? "cold" : temperature,
                    estimatedValue,
                    expectedCloseDate,
                    assignedTo,
                    nextFollowUpAt,
                    originRefType == null ? "manual" : originRefType,
                    originRefId,
                    originPayload == null || originPayload.isBlank() ? "{}" : originPayload,
                    notes,
                    items == null ? List.of() : items.stream().map(LeadItemRequest::toCommand).toList()
            );
        }
    }

    public record LeadItemRequest(
            Long productId,
            String productSku,
            @NotBlank String productName,
            @Pattern(regexp = "piece|set|box|meter") String unit,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice,
            @NotNull @DecimalMin("0") BigDecimal discountAmount,
            String notes
    ) {
        LeadService.LeadItem toCommand() {
            return new LeadService.LeadItem(
                    productId,
                    productSku,
                    productName,
                    unit == null ? "piece" : unit,
                    quantity,
                    unitPrice,
                    discountAmount,
                    notes
            );
        }
    }

    public record TransitionRequest(
            @Pattern(regexp = "contacted|consulting|quoted|negotiating|won|delivering|collecting|closed|lost")
            String stage,
            String lostReason,
            @PositiveOrZero long version
    ) {
    }
}
