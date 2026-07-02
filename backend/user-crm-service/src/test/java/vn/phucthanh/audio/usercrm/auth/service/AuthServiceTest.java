package vn.phucthanh.audio.usercrm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.usercrm.auth.client.SupabaseAuthClient;
import vn.phucthanh.audio.usercrm.auth.domain.UserAccount;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseSignupResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseTokenResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseUser;
import vn.phucthanh.audio.usercrm.auth.repository.AuthAuditLogRepository;
import vn.phucthanh.audio.usercrm.auth.repository.UserAccountRepository;

class AuthServiceTest {

    @Test
    void publicRegisterAlwaysCreatesCustomerAccount() {
        UUID userId = UUID.randomUUID();
        SupabaseAuthClient client = mock(SupabaseAuthClient.class);
        UserAccountRepository repository = mock(UserAccountRepository.class);
        AuthAuditLogRepository auditLogs = mock(AuthAuditLogRepository.class);
        OutboxPublisher outbox = mock(OutboxPublisher.class);
        when(client.signup("customer@example.com", "secret123", "Khách hàng"))
                .thenReturn(new SupabaseSignupResponse(
                        new SupabaseUser(
                                userId,
                                "customer@example.com",
                                Map.of("display_name", "Khách hàng"),
                                Map.of()
                        ),
                        "access-token",
                        "bearer",
                        3600,
                        "refresh-token"
                ));
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthService service = new AuthService(client, repository, auditLogs, outbox);
        AuthService.SessionResult result =
                service.register("customer@example.com", "secret123", "Khách hàng");

        assertThat(result.response().account().role()).isEqualTo("CUSTOMER");
        assertThat(result.response().account().accountType()).isEqualTo("customer");
    }

    @Test
    void loginCreatesMissingBusinessProfileAndReturnsSession() {
        UUID userId = UUID.randomUUID();
        SupabaseAuthClient client = mock(SupabaseAuthClient.class);
        UserAccountRepository repository = mock(UserAccountRepository.class);
        AuthAuditLogRepository auditLogs = mock(AuthAuditLogRepository.class);
        OutboxPublisher outbox = mock(OutboxPublisher.class);
        when(client.login("user@example.com", "secret123"))
                .thenReturn(new SupabaseTokenResponse(
                        "access-token",
                        "bearer",
                        3600,
                        "refresh-token",
                        new SupabaseUser(
                                userId,
                                "user@example.com",
                                Map.of("display_name", "Khách hàng"),
                                Map.of()
                        )
                ));
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthService service = new AuthService(client, repository, auditLogs, outbox);
        AuthService.SessionResult result = service.login("user@example.com", "secret123");

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.response().account().id()).isEqualTo(userId);
        assertThat(result.response().account().role()).isEqualTo("CUSTOMER");
        assertThat(result.response().account().displayName()).isEqualTo("Khách hàng");
        verify(repository).save(any(UserAccount.class));
    }
}
