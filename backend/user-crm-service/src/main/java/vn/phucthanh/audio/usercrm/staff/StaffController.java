package vn.phucthanh.audio.usercrm.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phucthanh.audio.shared.domain.BusinessCodes;
import vn.phucthanh.audio.shared.event.OutboxPublisher;

@RestController
@RequestMapping("/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public StaffController(NamedParameterJdbcTemplate jdbc, OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<Map<String, Object>> list() {
        return jdbc.queryForList(
                """
                select id, staff_code, full_name, display_name, phone, email,
                       role, department, position_title, auth_user_id, status,
                       hired_on, version, created_at, updated_at
                from public.staff_members
                order by status, full_name
                """,
                Map.of()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> create(@Valid @RequestBody CreateStaffRequest request) {
        UUID id = UUID.randomUUID();
        String code = BusinessCodes.next("NV");
        jdbc.update(
                """
                insert into public.staff_members (
                    id, staff_code, full_name, display_name, phone, email,
                    role, department, position_title, auth_user_id, status
                ) values (
                    :id, :code, :fullName, :displayName, :phone, :email,
                    :role, :department, :positionTitle, :authUserId, 'active'
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("code", code)
                        .addValue("fullName", request.fullName().trim())
                        .addValue("displayName", request.displayName())
                        .addValue("phone", request.phone())
                        .addValue("email", request.email())
                        .addValue("role", request.role())
                        .addValue("department", request.department())
                        .addValue("positionTitle", request.positionTitle())
                        .addValue("authUserId", request.authUserId())
        );
        outbox.publish("STAFF", id, "staff.created.v1", Map.of("staffId", id, "staffCode", code));
        return jdbc.queryForMap(
                "select * from public.staff_members where id = :id",
                Map.of("id", id)
        );
    }

    public record CreateStaffRequest(
            @NotBlank String fullName,
            String displayName,
            String phone,
            String email,
            @Pattern(regexp = "ceo|admin|manager|sales|technician|accountant|warehouse|support|operator|other")
            String role,
            @Pattern(regexp = "executive|sales|technical|accounting|warehouse|support|operations|other")
            String department,
            String positionTitle,
            UUID authUserId
    ) {
    }
}
