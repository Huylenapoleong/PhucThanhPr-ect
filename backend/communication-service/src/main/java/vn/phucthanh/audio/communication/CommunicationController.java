package vn.phucthanh.audio.communication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
public class CommunicationController {

    private final CommunicationService service;

    public CommunicationController(CommunicationService service) {
        this.service = service;
    }

    @PostMapping("/calls")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createCall(@Valid @RequestBody CallRequest request) {
        return service.createCall(new CommunicationService.CreateCall(
                request.externalCallId(), request.provider(), request.sourceChannel(),
                request.customerId(), request.direction(), request.phoneNumber(),
                request.callerName(), request.interactionMode(), request.route(),
                request.externalPayload() == null ? "{}" : request.externalPayload()
        ));
    }

    @GetMapping("/calls")
    List<Map<String, Object>> calls(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return service.calls(status, limit);
    }

    @PatchMapping("/calls/{id}/complete")
    Map<String, Object> complete(
            @PathVariable long id,
            @Valid @RequestBody CompleteCallRequest request
    ) {
        return service.completeCall(id, new CommunicationService.CompleteCall(
                request.intent(), request.intentConfidence(), request.customerRequirement(),
                request.transcript(), request.aiSummary(), request.aiResolution(),
                request.result(), request.handoffRequired(), request.transferReason(),
                request.leadId(), request.repairRequestId(), request.reminderId(), request.version()
        ));
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> message(@Valid @RequestBody MessageRequest request) {
        return service.queueMessage(new CommunicationService.QueueMessage(
                request.customerId(), request.reminderId(), request.channel(),
                request.recipient(), request.subject(), request.content(), request.idempotencyKey()
        ));
    }

    @GetMapping("/messages")
    List<Map<String, Object>> messages(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return service.messages(status, limit);
    }

    public record CallRequest(
            String externalCallId,
            @Pattern(regexp = "twilio|stringee|omicall|cloudfone|viettel|fpt_ai|zalo|manual|other")
            String provider,
            @Pattern(regexp = "web_call|phone|zalo|facebook|manual|other") String sourceChannel,
            Long customerId,
            @Pattern(regexp = "inbound|outbound") String direction,
            @NotBlank String phoneNumber,
            String callerName,
            @Pattern(regexp = "ivr|voice_agent|ivr_voice|human|manual") String interactionMode,
            @Pattern(regexp = "ai_reception|sales_quote|warranty_repair|accounting_debt|operator|general_support|unknown|other")
            String route,
            String externalPayload
    ) {
    }

    public record CompleteCallRequest(
            String intent,
            @DecimalMin("0") @DecimalMax("100") BigDecimal intentConfidence,
            String customerRequirement, String transcript, String aiSummary,
            String aiResolution,
            @Pattern(regexp = "ai_resolved|lead_created|repair_created|reminder_created|transferred|callback_required|missed|failed|no_answer|spam")
            String result,
            boolean handoffRequired, String transferReason, Long leadId,
            Long repairRequestId, Long reminderId, @PositiveOrZero long version
    ) {
    }

    public record MessageRequest(
            Long customerId, Long reminderId,
            @Pattern(regexp = "telegram|zalo|facebook|email|sms") String channel,
            @NotBlank String recipient, String subject, @NotBlank String content,
            String idempotencyKey
    ) {
    }
}
