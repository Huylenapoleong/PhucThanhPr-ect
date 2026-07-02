package vn.phucthanh.audio.commerce.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@PreAuthorize("hasAnyRole('SALES','ADMIN')")
public class CommerceDocumentController {

    private final CommerceDocumentService service;

    public CommerceDocumentController(CommerceDocumentService service) {
        this.service = service;
    }

    @PostMapping("/quotations")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createQuotation(@Valid @RequestBody QuotationRequest request) {
        return service.createQuotation(new CommerceDocumentService.CreateQuotation(
                request.customerId(), request.leadId(), request.validUntil(),
                request.headerDiscount(), request.paymentTerms(), request.deliveryTerms(),
                request.warrantyTerms(), request.notes(), request.createdBy(), toItems(request.items())
        ));
    }

    @GetMapping("/quotations/{id}")
    Map<String, Object> quotation(@PathVariable long id) {
        return service.getQuotation(id);
    }

    @PatchMapping("/quotations/{id}/approve")
    Map<String, Object> approve(
            @PathVariable long id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.approveQuotation(id, request.version());
    }

    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createContract(@Valid @RequestBody ContractRequest request) {
        return service.createContract(new CommerceDocumentService.CreateContract(
                request.quotationId(), request.signedDate(), request.startDate(),
                request.endDate(), request.paymentDueDate(), request.notes(), request.createdBy()
        ));
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return service.createInvoice(new CommerceDocumentService.CreateInvoice(
                request.customerId(), request.contractId(), request.quotationId(),
                request.buyerName(), request.buyerTaxCode(), request.buyerAddress(),
                request.buyerEmail(), request.issueDate(), request.dueDate(),
                request.headerDiscount(), request.notes(), request.createdBy(), toItems(request.items())
        ));
    }

    @GetMapping("/invoices/{id}")
    Map<String, Object> invoice(@PathVariable long id) {
        return service.getInvoice(id);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> payment(@Valid @RequestBody PaymentRequest request) {
        return service.recordPayment(new CommerceDocumentService.RecordPayment(
                request.customerId(), request.contractId(), request.invoiceId(), request.amount(),
                request.paymentMethod(), request.source(), request.paidAt(), request.provider(),
                request.externalTransactionId(), request.idempotencyKey(), request.bankCode(),
                request.payerName(), request.referenceText(), request.recordedBy(),
                request.sourcePayload() == null ? "{}" : request.sourcePayload(), request.notes()
        ));
    }

    private List<CommerceDocumentService.DocumentItem> toItems(List<DocumentItemRequest> items) {
        return items.stream().map(item -> new CommerceDocumentService.DocumentItem(
                item.sourceItemId(), item.productId(), item.productSku(), item.productName(),
                item.unit() == null ? "piece" : item.unit(), item.quantity(), item.unitPrice(),
                item.costPrice(), item.discountAmount(), item.taxRate(), item.notes()
        )).toList();
    }

    public record DocumentItemRequest(
            Long sourceItemId,
            Long productId,
            String productSku,
            @NotBlank String productName,
            @Pattern(regexp = "piece|set|box|meter") String unit,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice,
            @NotNull @DecimalMin("0") BigDecimal costPrice,
            @NotNull @DecimalMin("0") BigDecimal discountAmount,
            @NotNull @DecimalMin("0") BigDecimal taxRate,
            String notes
    ) {
    }

    public record QuotationRequest(
            long customerId,
            Long leadId,
            LocalDate validUntil,
            @NotNull @DecimalMin("0") BigDecimal headerDiscount,
            String paymentTerms,
            String deliveryTerms,
            String warrantyTerms,
            String notes,
            UUID createdBy,
            @NotNull List<@Valid DocumentItemRequest> items
    ) {
    }

    public record ContractRequest(
            long quotationId,
            LocalDate signedDate,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate paymentDueDate,
            String notes,
            UUID createdBy
    ) {
    }

    public record InvoiceRequest(
            long customerId,
            Long contractId,
            Long quotationId,
            @NotBlank String buyerName,
            String buyerTaxCode,
            String buyerAddress,
            String buyerEmail,
            LocalDate issueDate,
            LocalDate dueDate,
            @NotNull @DecimalMin("0") BigDecimal headerDiscount,
            String notes,
            UUID createdBy,
            @NotNull List<@Valid DocumentItemRequest> items
    ) {
    }

    public record PaymentRequest(
            long customerId,
            Long contractId,
            Long invoiceId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
            @Pattern(regexp = "bank_transfer|cash|card|payment_gateway|offset|other") String paymentMethod,
            @Pattern(regexp = "manual|bank_webhook|payment_gateway|misa|odoo|import|other") String source,
            @NotNull OffsetDateTime paidAt,
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

    public record VersionRequest(@PositiveOrZero long version) {
    }
}
