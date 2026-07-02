package vn.phucthanh.audio.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/automation")
public class AutomationController {

    private final AutomationService service;

    public AutomationController(AutomationService service) {
        this.service = service;
    }

    @GetMapping("/tools")
    List<Map<String, Object>> tools() {
        return List.of(
                Map.of("name", "search_products", "write", false),
                Map.of("name", "create_lead", "write", true, "confirmationRequired", true),
                Map.of("name", "create_repair", "write", true, "confirmationRequired", true)
        );
    }

    @PostMapping("/product-match")
    List<Map<String, Object>> match(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ProductMatchRequest request
    ) {
        return service.matchProducts(authorization, request.query(), request.size());
    }

    @PostMapping("/tools/create-lead")
    Map<String, Object> createLead(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody Map<String, Object> payload
    ) {
        return service.createLead(authorization, payload);
    }

    @PostMapping("/tools/create-repair")
    Map<String, Object> createRepair(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody Map<String, Object> payload
    ) {
        return service.createRepair(authorization, payload);
    }

    public record ProductMatchRequest(
            @NotBlank String query,
            @Min(1) @Max(20) int size
    ) {
    }
}
