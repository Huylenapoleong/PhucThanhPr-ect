package vn.phucthanh.audio.usercrm.auth.service;

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
import vn.phucthanh.audio.usercrm.auth.client.SupabaseAdminAuthClient;
import vn.phucthanh.audio.usercrm.auth.domain.AuthAuditLog;
import vn.phucthanh.audio.usercrm.auth.domain.UserAccount;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.InviteSalesRequest;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.StaffAccountResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseUser;
import vn.phucthanh.audio.usercrm.auth.repository.AuthAuditLogRepository;
import vn.phucthanh.audio.usercrm.auth.repository.UserAccountRepository;

@Service
public class AccountProvisioningService {

    private final SupabaseAdminAuthClient adminAuthClient;
    private final UserAccountRepository accounts;
    private final AuthAuditLogRepository auditLogs;
    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public AccountProvisioningService(
            SupabaseAdminAuthClient adminAuthClient,
            UserAccountRepository accounts,
            AuthAuditLogRepository auditLogs,
            NamedParameterJdbcTemplate jdbc,
            OutboxPublisher outbox
    ) {
        this.adminAuthClient = adminAuthClient;
        this.accounts = accounts;
        this.auditLogs = auditLogs;
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional
    public StaffAccountResponse inviteSales(UUID actorUserId, InviteSalesRequest request) {
        String email = normalizeEmail(request.email());
        String fullName = request.fullName().trim();
        String displayName = displayName(request.displayName(), fullName);
        SupabaseUser invitedUser = adminAuthClient.inviteUser(email, displayName);

        try {
            return provisionStaff(
                    invitedUser.id(),
                    actorUserId,
                    email,
                    fullName,
                    displayName,
                    normalizeNullable(request.phone()),
                    normalizeNullable(request.positionTitle()),
                    "SALES",
                    "sales",
                    "sales",
                    true,
                    "admin"
            );
        } catch (RuntimeException exception) {
            adminAuthClient.deleteUserQuietly(invitedUser.id());
            throw exception;
        }
    }

    @Transactional
    public BootstrapResult bootstrapFirstAdmin(String rawEmail, String rawDisplayName) {
        String email = normalizeEmail(rawEmail);
        String displayName = displayName(rawDisplayName, "Administrator");

        jdbc.getJdbcTemplate().execute(
                "select pg_advisory_xact_lock(hashtext('phucthanh:first-admin-bootstrap'))"
        );

        List<UUID> existingAdmins = jdbc.query(
                """
                select id
                from public.user_accounts
                where role = 'ADMIN'
                order by created_at
                limit 1
                """,
                Map.of(),
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
        );
        if (!existingAdmins.isEmpty()) {
            return new BootstrapResult(existingAdmins.get(0), false, false);
        }

        List<UUID> authUsers = jdbc.query(
                """
                select id
                from auth.users
                where lower(email) = :email
                order by created_at
                limit 1
                """,
                Map.of("email", email),
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
        );

        boolean invitationSent = authUsers.isEmpty();
        UUID userId = invitationSent
                ? adminAuthClient.inviteUser(email, displayName).id()
                : authUsers.get(0);

        try {
            provisionStaff(
                    userId,
                    null,
                    email,
                    displayName,
                    displayName,
                    null,
                    "Administrator",
                    "ADMIN",
                    "admin",
                    "executive",
                    invitationSent,
                    "system"
            );
            return new BootstrapResult(userId, true, invitationSent);
        } catch (RuntimeException exception) {
            if (invitationSent) {
                adminAuthClient.deleteUserQuietly(userId);
            }
            throw exception;
        }
    }

    private StaffAccountResponse provisionStaff(
            UUID userId,
            UUID actorUserId,
            String email,
            String fullName,
            String displayName,
            String phone,
            String positionTitle,
            String accountRole,
            String staffRole,
            String department,
            boolean invitationSent,
            String auditSource
    ) {
        UserAccount account = accounts.findById(userId)
                .map(existing -> {
                    existing.changeRole(accountRole);
                    return existing;
                })
                .orElseGet(() -> UserAccount.staff(userId, displayName, accountRole));
        accounts.saveAndFlush(account);

        StaffIdentity staff = upsertStaff(
                userId,
                email,
                fullName,
                displayName,
                phone,
                positionTitle,
                staffRole,
                department
        );

        auditLogs.saveAndFlush(AuthAuditLog.success(
                userId,
                actorUserId,
                invitationSent ? "invite_sent" : "other",
                auditSource,
                Map.of(
                        "action", invitationSent ? "staff_invited" : "staff_role_assigned",
                        "role", accountRole,
                        "staffId", staff.id()
                )
        ));
        outbox.publish("USER_ACCOUNT", userId, "user.role-changed.v1", Map.of(
                "userId", userId,
                "role", accountRole,
                "sessionVersion", account.getSessionVersion()
        ));
        outbox.publish("STAFF", staff.id(), "staff.provisioned.v1", Map.of(
                "staffId", staff.id(),
                "userId", userId,
                "role", staffRole
        ));

        return new StaffAccountResponse(
                userId,
                staff.id(),
                staff.code(),
                email,
                displayName,
                account.getRole(),
                account.getAccountType(),
                account.getStatus(),
                invitationSent
        );
    }

    private StaffIdentity upsertStaff(
            UUID userId,
            String email,
            String fullName,
            String displayName,
            String phone,
            String positionTitle,
            String role,
            String department
    ) {
        List<Map<String, Object>> matches = jdbc.queryForList(
                """
                select id, staff_code, auth_user_id
                from public.staff_members
                where auth_user_id = :userId
                   or lower(email) = :email
                for update
                """,
                Map.of("userId", userId, "email", email)
        );
        if (matches.size() > 1) {
            throw new BusinessException(
                    409,
                    "STAFF_ACCOUNT_CONFLICT",
                    "Email và tài khoản đang thuộc hai hồ sơ nhân viên khác nhau"
            );
        }

        if (!matches.isEmpty()) {
            Map<String, Object> match = matches.get(0);
            UUID linkedUserId = (UUID) match.get("auth_user_id");
            if (linkedUserId != null && !linkedUserId.equals(userId)) {
                throw new BusinessException(
                        409,
                        "STAFF_EMAIL_ALREADY_EXISTS",
                        "Email đã thuộc một nhân viên khác"
                );
            }
            UUID staffId = (UUID) match.get("id");
            String staffCode = (String) match.get("staff_code");
            jdbc.update(
                    """
                    update public.staff_members
                    set full_name = :fullName,
                        display_name = :displayName,
                        phone = :phone,
                        email = :email,
                        role = :role,
                        department = :department,
                        position_title = :positionTitle,
                        auth_user_id = :userId,
                        status = 'active'
                    where id = :staffId
                    """,
                    staffParameters(
                            staffId,
                            staffCode,
                            userId,
                            email,
                            fullName,
                            displayName,
                            phone,
                            positionTitle,
                            role,
                            department
                    )
            );
            return new StaffIdentity(staffId, staffCode);
        }

        UUID staffId = UUID.randomUUID();
        String staffCode = BusinessCodes.next("NV");
        jdbc.update(
                """
                insert into public.staff_members (
                    id, staff_code, full_name, display_name, phone, email,
                    role, department, position_title, auth_user_id, status
                ) values (
                    :staffId, :staffCode, :fullName, :displayName, :phone, :email,
                    :role, :department, :positionTitle, :userId, 'active'
                )
                """,
                staffParameters(
                        staffId,
                        staffCode,
                        userId,
                        email,
                        fullName,
                        displayName,
                        phone,
                        positionTitle,
                        role,
                        department
                )
        );
        return new StaffIdentity(staffId, staffCode);
    }

    private MapSqlParameterSource staffParameters(
            UUID staffId,
            String staffCode,
            UUID userId,
            String email,
            String fullName,
            String displayName,
            String phone,
            String positionTitle,
            String role,
            String department
    ) {
        return new MapSqlParameterSource()
                .addValue("staffId", staffId)
                .addValue("staffCode", staffCode)
                .addValue("userId", userId)
                .addValue("email", email)
                .addValue("fullName", fullName)
                .addValue("displayName", displayName)
                .addValue("phone", phone)
                .addValue("positionTitle", positionTitle)
                .addValue("role", role)
                .addValue("department", department);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String displayName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback.trim() : value.trim();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record StaffIdentity(UUID id, String code) {
    }

    public record BootstrapResult(UUID userId, boolean bootstrapped, boolean invitationSent) {
    }
}
