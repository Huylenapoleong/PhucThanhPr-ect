package vn.phucthanh.audio.usercrm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.usercrm.auth.client.SupabaseAdminAuthClient;
import vn.phucthanh.audio.usercrm.auth.domain.AuthAuditLog;
import vn.phucthanh.audio.usercrm.auth.domain.UserAccount;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.InviteSalesRequest;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.StaffAccountResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseUser;
import vn.phucthanh.audio.usercrm.auth.repository.AuthAuditLogRepository;
import vn.phucthanh.audio.usercrm.auth.repository.UserAccountRepository;

class AccountProvisioningServiceTest {

    @Test
    void adminInviteCreatesSalesAccountAndStaffProfile() {
        UUID actorUserId = UUID.randomUUID();
        UUID salesUserId = UUID.randomUUID();
        SupabaseAdminAuthClient adminClient = mock(SupabaseAdminAuthClient.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthAuditLogRepository auditLogs = mock(AuthAuditLogRepository.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        OutboxPublisher outbox = mock(OutboxPublisher.class);

        when(adminClient.inviteUser("sales@example.com", "Sales A"))
                .thenReturn(new SupabaseUser(
                        salesUserId,
                        "sales@example.com",
                        Map.of("display_name", "Sales A"),
                        Map.of()
                ));
        when(accounts.findById(salesUserId)).thenReturn(Optional.empty());
        when(accounts.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogs.saveAndFlush(any(AuthAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        AccountProvisioningService service = new AccountProvisioningService(
                adminClient,
                accounts,
                auditLogs,
                jdbc,
                outbox
        );

        StaffAccountResponse response = service.inviteSales(
                actorUserId,
                new InviteSalesRequest(
                        " Sales@Example.com ",
                        "Nguyễn Văn Sales",
                        "Sales A",
                        "0901234567",
                        "Nhân viên kinh doanh"
                )
        );

        assertThat(response.userId()).isEqualTo(salesUserId);
        assertThat(response.email()).isEqualTo("sales@example.com");
        assertThat(response.role()).isEqualTo("SALES");
        assertThat(response.accountType()).isEqualTo("staff");
        assertThat(response.invitationSent()).isTrue();
        verify(adminClient).inviteUser("sales@example.com", "Sales A");
        verify(accounts).saveAndFlush(any(UserAccount.class));
        verify(jdbc).update(anyString(), any(MapSqlParameterSource.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void bootstrapPromotesExistingCustomerToFirstAdminOnlyOnce() {
        UUID userId = UUID.randomUUID();
        SupabaseAdminAuthClient adminClient = mock(SupabaseAdminAuthClient.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthAuditLogRepository auditLogs = mock(AuthAuditLogRepository.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OutboxPublisher outbox = mock(OutboxPublisher.class);
        UserAccount customer = UserAccount.customer(userId, "Owner");

        when(jdbc.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(jdbc.query(
                anyString(),
                anyMap(),
                any(RowMapper.class)
        )).thenReturn(List.of(), List.of(userId));
        when(accounts.findById(userId)).thenReturn(Optional.of(customer));
        when(accounts.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogs.saveAndFlush(any(AuthAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        AccountProvisioningService service = new AccountProvisioningService(
                adminClient,
                accounts,
                auditLogs,
                jdbc,
                outbox
        );

        AccountProvisioningService.BootstrapResult result =
                service.bootstrapFirstAdmin("owner@example.com", "Owner");

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).saveAndFlush(accountCaptor.capture());
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.bootstrapped()).isTrue();
        assertThat(result.invitationSent()).isFalse();
        assertThat(accountCaptor.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(accountCaptor.getValue().getAccountType()).isEqualTo("hybrid");
    }
}
