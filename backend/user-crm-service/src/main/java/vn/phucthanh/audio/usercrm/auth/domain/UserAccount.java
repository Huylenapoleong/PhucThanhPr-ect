package vn.phucthanh.audio.usercrm.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_accounts", schema = "public")
public class UserAccount {

    @Id
    private UUID id;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false)
    private String locale;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "mfa_required", nullable = false)
    private boolean mfaRequired;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    @Column(name = "lock_reason")
    private String lockReason;

    @Column(name = "session_version", nullable = false)
    private long sessionVersion;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserAccount() {
    }

    public static UserAccount customer(UUID id, String displayName) {
        UserAccount account = new UserAccount();
        account.id = id;
        account.accountType = "customer";
        account.role = "CUSTOMER";
        account.status = "active";
        account.displayName = displayName;
        account.locale = "vi-VN";
        account.timeZone = "Asia/Ho_Chi_Minh";
        return account;
    }

    public static UserAccount staff(UUID id, String displayName, String role) {
        UserAccount account = new UserAccount();
        account.id = id;
        account.accountType = "staff";
        account.role = role;
        account.status = "active";
        account.displayName = displayName;
        account.locale = "vi-VN";
        account.timeZone = "Asia/Ho_Chi_Minh";
        return account;
    }

    public void markLogin() {
        OffsetDateTime now = OffsetDateTime.now();
        lastLoginAt = now;
        lastSeenAt = now;
    }

    public boolean isUsable() {
        return "active".equals(status);
    }

    public void changeRole(String role) {
        this.role = role;
        if (!"CUSTOMER".equals(role) && "customer".equals(accountType)) {
            accountType = "hybrid";
        }
        sessionVersion++;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getLocale() {
        return locale;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public boolean isMfaRequired() {
        return mfaRequired;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public long getSessionVersion() {
        return sessionVersion;
    }

    public long getVersion() {
        return version;
    }
}
