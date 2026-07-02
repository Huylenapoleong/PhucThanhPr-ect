package vn.phucthanh.audio.usercrm.auth.service;

import java.util.UUID;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.shared.web.BusinessException;
import vn.phucthanh.audio.usercrm.auth.client.SupabaseAuthClient;
import vn.phucthanh.audio.usercrm.auth.domain.AuthAuditLog;
import vn.phucthanh.audio.usercrm.auth.domain.UserAccount;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.AccountResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.AuthResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.RoleResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseSignupResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseTokenResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseUser;
import vn.phucthanh.audio.usercrm.auth.repository.AuthAuditLogRepository;
import vn.phucthanh.audio.usercrm.auth.repository.UserAccountRepository;

@Service
public class AuthService {

    private final SupabaseAuthClient authClient;
    private final UserAccountRepository accounts;
    private final AuthAuditLogRepository auditLogs;
    private final OutboxPublisher outbox;

    public AuthService(
            SupabaseAuthClient authClient,
            UserAccountRepository accounts,
            AuthAuditLogRepository auditLogs,
            OutboxPublisher outbox
    ) {
        this.authClient = authClient;
        this.accounts = accounts;
        this.auditLogs = auditLogs;
        this.outbox = outbox;
    }

    @Transactional
    public SessionResult register(String email, String password, String displayName) {
        SupabaseSignupResponse response = authClient.signup(email, password, displayName);
        if (response == null || response.user() == null) {
            throw new BusinessException(502, "INVALID_AUTH_RESPONSE", "Supabase Auth trả về dữ liệu không hợp lệ");
        }
        UserAccount account = accounts.findById(response.user().id())
                .orElseGet(() -> UserAccount.customer(response.user().id(), displayName.trim()));
        accounts.save(account);
        auditLogs.save(AuthAuditLog.success(
                account.getId(),
                "signup",
                java.util.Map.of("emailConfirmationRequired", confirmationRequired(response))
        ));
        outbox.publish("USER_ACCOUNT", account.getId(), "user.registered.v1", java.util.Map.of(
                "userId", account.getId(),
                "email", email
        ));
        boolean confirmationRequired = confirmationRequired(response);
        return new SessionResult(
                new AuthResponse(
                        response.accessToken(),
                        response.tokenType(),
                        response.expiresIn(),
                        account(account, response.user().email()),
                        confirmationRequired
                ),
                response.refreshToken()
        );
    }

    @Transactional
    public SessionResult login(String email, String password) {
        SupabaseTokenResponse response = authClient.login(email, password);
        return acceptSession(response);
    }

    @Transactional
    public SessionResult refresh(String refreshToken) {
        return acceptSession(authClient.refresh(refreshToken));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        authClient.requestPasswordReset(email);
        auditLogs.save(AuthAuditLog.success(
                null,
                "password_reset_requested",
                java.util.Map.of("email", email)
        ));
    }

    @Transactional(readOnly = true)
    public AccountResponse me(UUID userId, String email) {
        UserAccount account = accounts.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "ACCOUNT_NOT_FOUND", "Chưa có hồ sơ người dùng"));
        ensureUsable(account);
        return account(account, email);
    }

    @Transactional
    public RoleResponse changeRole(UUID userId, String role) {
        UserAccount account = accounts.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản"));
        String normalizedRole = normalizeRole(role);
        account.changeRole(normalizedRole);
        accounts.save(account);
        auditLogs.save(AuthAuditLog.success(
                account.getId(),
                "other",
                java.util.Map.of("action", "role_changed", "role", normalizedRole)
        ));
        outbox.publish("USER_ACCOUNT", account.getId(), "user.role-changed.v1", java.util.Map.of(
                "userId", account.getId(),
                "role", normalizedRole,
                "sessionVersion", account.getSessionVersion()
        ));
        return new RoleResponse(account.getId(), account.getRole(), account.getSessionVersion());
    }

    @Transactional
    public void logout(String accessToken, UUID userId) {
        authClient.logout(accessToken);
        auditLogs.save(AuthAuditLog.success(userId, "logout", java.util.Map.of()));
    }

    private SessionResult acceptSession(SupabaseTokenResponse response) {
        if (response == null || response.user() == null || response.accessToken() == null) {
            throw new BusinessException(502, "INVALID_AUTH_RESPONSE", "Supabase Auth trả về dữ liệu không hợp lệ");
        }
        SupabaseUser authUser = response.user();
        String displayName = metadataDisplayName(authUser);
        UserAccount account = accounts.findById(authUser.id())
                .orElseGet(() -> UserAccount.customer(authUser.id(), displayName));
        ensureUsable(account);
        account.markLogin();
        accounts.save(account);
        auditLogs.save(AuthAuditLog.success(account.getId(), "login_success", java.util.Map.of()));
        return new SessionResult(
                new AuthResponse(
                        response.accessToken(),
                        response.tokenType(),
                        response.expiresIn(),
                        account(account, authUser.email()),
                        false
                ),
                response.refreshToken()
        );
    }

    private void ensureUsable(UserAccount account) {
        if (!account.isUsable()) {
            throw new BusinessException(403, "ACCOUNT_BLOCKED", "Tài khoản đang bị khóa hoặc vô hiệu hóa");
        }
    }

    private String metadataDisplayName(SupabaseUser user) {
        if (user.userMetadata() == null) {
            return user.email();
        }
        Object value = user.userMetadata().get("display_name");
        return value instanceof String text && !text.isBlank() ? text.trim() : user.email();
    }

    private boolean confirmationRequired(SupabaseSignupResponse response) {
        return response.accessToken() == null || response.accessToken().isBlank();
    }

    private AccountResponse account(UserAccount account, String email) {
        return new AccountResponse(
                account.getId(),
                email,
                account.getAccountType(),
                account.getRole(),
                account.getStatus(),
                account.getDisplayName(),
                account.getAvatarUrl(),
                account.getLocale(),
                account.getTimeZone(),
                account.isMfaRequired(),
                account.getSessionVersion()
        );
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("ADMIN", "SALES", "CUSTOMER").contains(normalized)) {
            throw new BusinessException(400, "INVALID_ROLE", "Role phải là ADMIN, SALES hoặc CUSTOMER");
        }
        return normalized;
    }

    public record SessionResult(AuthResponse response, String refreshToken) {
    }
}
