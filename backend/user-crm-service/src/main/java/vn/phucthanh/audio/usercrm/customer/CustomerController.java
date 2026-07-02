package vn.phucthanh.audio.usercrm.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    List<Map<String, Object>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.search(query, status, page, size);
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    Map<String, Object> get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> create(@Valid @RequestBody CreateCustomerRequest request) {
        return service.create(request.toCommand());
    }

    @PatchMapping("/customers/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    Map<String, Object> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        return service.changeStatus(id, request.status(), request.version());
    }

    @GetMapping("/users/me")
    Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return service.currentAccount(UUID.fromString(jwt.getSubject()));
    }

    public record CreateCustomerRequest(
            @Pattern(regexp = "individual|business") String customerType,
            @NotBlank String displayName,
            String legalName,
            String taxCode,
            String legalRepresentative,
            String representativeTitle,
            String registeredAddress,
            String billingAddress,
            String primaryContactName,
            String primaryPhone,
            String primaryEmail,
            String website,
            @Pattern(regexp = "web|facebook|zalo|call|dauthau|referral|manual|other") String source,
            String notes
    ) {
        CustomerService.CreateCustomer toCommand() {
            return new CustomerService.CreateCustomer(
                    customerType == null ? "business" : customerType,
                    displayName,
                    legalName,
                    taxCode,
                    legalRepresentative,
                    representativeTitle,
                    registeredAddress,
                    billingAddress,
                    primaryContactName,
                    primaryPhone,
                    primaryEmail,
                    website,
                    source == null ? "manual" : source,
                    notes
            );
        }
    }

    public record ChangeStatusRequest(
            @Pattern(regexp = "active|inactive|blocked") String status,
            @PositiveOrZero long version
    ) {
    }
}
