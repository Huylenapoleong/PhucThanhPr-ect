package vn.phucthanh.audio.usercrm.auth.client;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.phucthanh.audio.shared.web.BusinessException;
import vn.phucthanh.audio.usercrm.auth.config.SupabaseAuthProperties;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseUser;

@Component
public class SupabaseAdminAuthClient {

    private final RestClient restClient;
    private final SupabaseAuthProperties properties;
    private final ObjectMapper objectMapper;

    public SupabaseAdminAuthClient(
            @Qualifier("supabaseAdminRestClient") RestClient restClient,
            SupabaseAuthProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SupabaseUser inviteUser(String email, String displayName) {
        String secretKey = requiredSecretKey();
        try {
            SupabaseUser user = restClient.post()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/auth/v1/invite");
                        if (properties.emailRedirectUrl() != null
                                && !properties.emailRedirectUrl().isBlank()) {
                            builder.queryParam("redirect_to", properties.emailRedirectUrl());
                        }
                        return builder.build();
                    })
                    .headers(headers -> adminHeaders(headers, secretKey))
                    .body(Map.of(
                            "email", email,
                            "data", Map.of("display_name", displayName)
                    ))
                    .retrieve()
                    .body(SupabaseUser.class);
            if (user == null || user.id() == null) {
                throw new BusinessException(
                        502,
                        "INVALID_AUTH_RESPONSE",
                        "Supabase Auth trả về dữ liệu không hợp lệ"
                );
            }
            return user;
        } catch (RestClientResponseException exception) {
            throw inviteError(exception);
        } catch (ResourceAccessException exception) {
            throw unavailableError();
        }
    }

    public void deleteUserQuietly(UUID userId) {
        if (userId == null || properties.secretKey() == null || properties.secretKey().isBlank()) {
            return;
        }
        try {
            restClient.delete()
                    .uri("/auth/v1/admin/users/{userId}", userId)
                    .headers(headers -> adminHeaders(headers, properties.secretKey().trim()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // Best-effort compensation. The original provisioning error is more useful to the caller.
        }
    }

    private void adminHeaders(HttpHeaders headers, String secretKey) {
        headers.set("apikey", secretKey);
        headers.setBearerAuth(secretKey);
    }

    private String requiredSecretKey() {
        String secretKey = properties.secretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new BusinessException(
                    503,
                    "SUPABASE_ADMIN_NOT_CONFIGURED",
                    "Backend chưa được cấu hình Supabase secret key"
            );
        }
        return secretKey.trim();
    }

    private BusinessException inviteError(RestClientResponseException exception) {
        return switch (supabaseErrorCode(exception)) {
            case "user_already_exists", "email_exists" ->
                    new BusinessException(409, "EMAIL_ALREADY_REGISTERED", "Email đã được đăng ký");
            case "over_email_send_rate_limit", "over_request_rate_limit" ->
                    new BusinessException(429, "AUTH_RATE_LIMITED", "Bạn thao tác quá nhanh, vui lòng thử lại sau");
            case "bad_jwt", "invalid_token" ->
                    new BusinessException(
                            503,
                            "SUPABASE_ADMIN_NOT_CONFIGURED",
                            "Supabase secret key không hợp lệ"
                    );
            default -> {
                int status = exception.getStatusCode().value();
                int safeStatus = status >= 400 && status < 500 ? status : 502;
                yield new BusinessException(
                        safeStatus,
                        "SUPABASE_ADMIN_ERROR",
                        "Không thể mời tài khoản nhân viên"
                );
            }
        };
    }

    private String supabaseErrorCode(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        if (headers != null) {
            String headerCode = normalized(headers.getFirst("X-Sb-Error-Code"));
            if (headerCode != null) {
                return headerCode;
            }
        }
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            String errorCode = text(body, "error_code");
            return errorCode != null ? errorCode : text(body, "code");
        } catch (JacksonException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private String text(JsonNode body, String field) {
        if (body == null) {
            return null;
        }
        JsonNode value = body.path(field);
        return value.isTextual() ? normalized(value.textValue()) : null;
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException unavailableError() {
        return new BusinessException(
                502,
                "SUPABASE_AUTH_UNAVAILABLE",
                "Không thể kết nối tới dịch vụ xác thực"
        );
    }
}
