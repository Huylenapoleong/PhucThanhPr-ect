package vn.phucthanh.audio.reporting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@PreAuthorize("hasRole('ADMIN')")
public class KpiController {

    private final KpiService service;

    public KpiController(KpiService service) {
        this.service = service;
    }

    @GetMapping({"/kpis", "/reports/kpis"})
    List<Map<String, Object>> list(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String periodType
    ) {
        return service.list(from, to, periodType);
    }

    @PostMapping({"/kpis/generate", "/reports/kpis/generate"})
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> generate(@Valid @RequestBody GenerateRequest request) {
        if (request.end().isBefore(request.start())) {
            throw new IllegalArgumentException("period end must not be before start");
        }
        return service.generate(request.start(), request.end(), request.periodType());
    }

    public record GenerateRequest(
            @NotNull LocalDate start,
            @NotNull LocalDate end,
            @Pattern(regexp = "day|week|month|quarter|year|custom") String periodType
    ) {
    }
}
