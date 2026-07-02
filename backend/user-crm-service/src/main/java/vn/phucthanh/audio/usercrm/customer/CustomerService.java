package vn.phucthanh.audio.usercrm.customer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phucthanh.audio.shared.domain.BusinessCodes;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.shared.web.BusinessException;

@Service
public class CustomerService {

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    public CustomerService(NamedParameterJdbcTemplate jdbc, OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(String query, String status, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return jdbc.queryForList(
                """
                select id, customer_code, customer_type, display_name, legal_name, tax_code,
                       primary_contact_name, primary_phone, primary_email, source,
                       tax_verification_status, status, version, created_at, updated_at
                from public.customers
                where (:query is null
                       or lower(display_name) like :query
                       or lower(coalesce(tax_code, '')) like :query
                       or lower(coalesce(primary_phone, '')) like :query)
                  and (:status is null or status = :status)
                order by updated_at desc, id desc
                limit :limit offset :offset
                """,
                new MapSqlParameterSource()
                        .addValue("query", normalized)
                        .addValue("status", blankToNull(status))
                        .addValue("limit", safeSize)
                        .addValue("offset", safePage * safeSize)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        return jdbc.query(
                "select * from public.customers where id = :id",
                Map.of("id", id),
                resultSet -> {
                    if (!resultSet.next()) {
                        throw notFound(id);
                    }
                    return toMap(resultSet);
                }
        );
    }

    @Transactional
    public Map<String, Object> create(CreateCustomer command) {
        String code = BusinessCodes.next("KH");
        Long id = jdbc.queryForObject(
                """
                insert into public.customers (
                    customer_code, customer_type, display_name, legal_name, tax_code,
                    legal_representative, representative_title, registered_address,
                    billing_address, primary_contact_name, primary_phone, primary_email,
                    website, source, status, notes
                ) values (
                    :code, :type, :displayName, :legalName, :taxCode,
                    :legalRepresentative, :representativeTitle, :registeredAddress,
                    :billingAddress, :contactName, :phone, :email,
                    :website, :source, 'active', :notes
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("type", command.customerType())
                        .addValue("displayName", command.displayName().trim())
                        .addValue("legalName", blankToNull(command.legalName()))
                        .addValue("taxCode", blankToNull(command.taxCode()))
                        .addValue("legalRepresentative", blankToNull(command.legalRepresentative()))
                        .addValue("representativeTitle", blankToNull(command.representativeTitle()))
                        .addValue("registeredAddress", blankToNull(command.registeredAddress()))
                        .addValue("billingAddress", blankToNull(command.billingAddress()))
                        .addValue("contactName", blankToNull(command.primaryContactName()))
                        .addValue("phone", blankToNull(command.primaryPhone()))
                        .addValue("email", blankToNull(command.primaryEmail()))
                        .addValue("website", blankToNull(command.website()))
                        .addValue("source", command.source())
                        .addValue("notes", blankToNull(command.notes())),
                Long.class
        );
        outbox.publish("CUSTOMER", id, "customer.created.v1", Map.of("customerId", id, "customerCode", code));
        return get(id);
    }

    @Transactional
    public Map<String, Object> changeStatus(long id, String status, long expectedVersion) {
        int updated = jdbc.update(
                """
                update public.customers
                set status = :status, version = version + 1, updated_at = now()
                where id = :id and version = :version
                """,
                Map.of("id", id, "status", status, "version", expectedVersion)
        );
        if (updated == 0) {
            ensureExistsOrConflict(id);
        }
        outbox.publish("CUSTOMER", id, "customer.status-changed.v1", Map.of("customerId", id, "status", status));
        return get(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> currentAccount(UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                select ua.id, ua.account_type, ua.status, ua.display_name, ua.avatar_url,
                       ua.locale, ua.time_zone, ua.mfa_required, ua.session_version,
                       ua.last_login_at, ua.last_seen_at
                from public.user_accounts ua
                where ua.id = :id
                """,
                Map.of("id", userId)
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "ACCOUNT_NOT_FOUND", "Chưa có hồ sơ tài khoản nghiệp vụ");
        }
        return rows.get(0);
    }

    private void ensureExistsOrConflict(long id) {
        Integer count = jdbc.queryForObject(
                "select count(*) from public.customers where id = :id",
                Map.of("id", id),
                Integer.class
        );
        if (count == null || count == 0) {
            throw notFound(id);
        }
        throw new BusinessException(409, "CONCURRENT_UPDATE", "Khách hàng vừa được cập nhật bởi yêu cầu khác");
    }

    private Map<String, Object> toMap(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        java.sql.ResultSetMetaData metadata = resultSet.getMetaData();
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
        }
        return row;
    }

    private BusinessException notFound(long id) {
        return new BusinessException(HttpStatus.NOT_FOUND.value(), "CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng " + id);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateCustomer(
            String customerType,
            String displayName,
            String legalName,
            String taxCode,
            String legalRepresentative,
            String representativeTitle,
            String registeredAddress,
            String billingAddress,
            String primaryContactName,
            String primaryPhone,
            String primaryEmail,
            String website,
            String source,
            String notes
    ) {
    }
}
