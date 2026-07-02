package vn.phucthanh.audio.usercrm.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phucthanh.audio.usercrm.auth.domain.AuthAuditLog;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {
}
