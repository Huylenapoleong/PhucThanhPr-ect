package vn.phucthanh.audio.usercrm.auth.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phucthanh.audio.usercrm.auth.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
}
