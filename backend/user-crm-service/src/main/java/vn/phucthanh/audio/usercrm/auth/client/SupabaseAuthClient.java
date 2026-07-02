package vn.phucthanh.audio.usercrm.auth.client;

import java.util.Map;
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
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseSignupResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseTokenResponse;

@Component
public class SupabaseAuthClient {

    private final RestClient restClient;
    private final SupabaseAuthProperties properties;
    private final ObjectMapper objectMapper;

    public SupabaseAuthClient(
            @Qualifier("supabaseAuthRestClient")
            RestClient supabaseAuthRestClient,
            SupabaseAuthProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = supabaseAuthRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SupabaseSignupResponse signup(String email, String password, String displayName) {
        try {
            return restClient.post()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/auth/v1/signup");
                        if (properties.emailRedirectUrl() != null
                                && !properties.emailRedirectUrl().isBlank()) {
                            builder.queryParam("redirect_to", properties.emailRedirectUrl());
                        }
                        return builder.build();
                    })
                    .body(Map.of(
                            "email", email,
                            "password", password,
                            "data", Map.of("display_name", displayName)
                    ))
                    .retrieve()
                    .body(SupabaseSignupResponse.class);
        } catch (RestClientResponseException exception) {
            throw signupError(exception);
        } catch (ResourceAccessException exception) {
            throw unavailableError();
        }
    }

    public SupabaseTokenResponse login(String email, String password) {
        try {
            return restClient.post()
                    .uri("/auth/v1/token?grant_type=password")
                    .body(Map.of("email", email, "password", password))
                    .retrieve()
                    .body(SupabaseTokenResponse.class);
        } catch (RestClientResponseException exception) {
            throw authError(exception, "Email hoặc mật khẩu không đúng");
        } catch (ResourceAccessException exception) {
            throw unavailableError();
        }
    }

    public SupabaseTokenResponse refresh(String refreshToken) {
        try {
            return restClient.post()
                    .uri("/auth/v1/token?grant_type=refresh_token")
                    .body(Map.of("refresh_token", refreshToken))
                    .retrieve()
                    .body(SupabaseTokenResponse.class);
        } catch (RestClientResponseException exception) {
            throw new BusinessException(401, "INVALID_REFRESH_TOKEN", "Phiên đăng nhập đã hết hạn");
        } catch (ResourceAccessException exception) {
            throw unavailableError();
        }
    }

    public void requestPasswordReset(String email) {
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/auth/v1/recover")
                            .queryParam("redirect_to", properties.emailRedirectUrl())
                            .build())
                    .body(Map.of("email", email))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw authError(exception, "Không thể gửi email đặt lại mật khẩu");
        } catch (ResourceAccessException exception) {
            throw unavailableError();
        }
    }

    public void logout(String accessToken) {
        try {
            restClient.post()
                    .uri("/auth/v1/logout?scope=local")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw authError(exception, "Không thể đăng xuất");
        } catch (ResourceAccessException exception) {
            throw unavailableError();
        }
    }

    private BusinessException signupError(RestClientResponseException exception) {
        return switch (supabaseErrorCode(exception)) {
            case "user_already_exists", "email_exists" ->
                    new BusinessException(409, "EMAIL_ALREADY_REGISTERED", "Email đã được đăng ký");
            case "weak_password" ->
                    new BusinessException(422, "WEAK_PASSWORD", "Mật khẩu chưa đáp ứng yêu cầu bảo mật");
            case "email_address_invalid" ->
                    new BusinessException(422, "INVALID_EMAIL", "Email không hợp lệ");
            case "signup_disabled", "email_provider_disabled" ->
                    new BusinessException(403, "SIGNUP_DISABLED", "Chức năng đăng ký đang bị tắt");
            case "over_email_send_rate_limit", "over_request_rate_limit" ->
                    new BusinessException(429, "AUTH_RATE_LIMITED", "Bạn thao tác quá nhanh, vui lòng thử lại sau");
            default -> authError(exception, "Không thể đăng ký tài khoản");
        };
    }

    private BusinessException authError(RestClientResponseException exception, String fallback) {
        int status = exception.getStatusCode().value();
        int safeStatus = status >= 400 && status < 500 ? status : 502;
        return new BusinessException(safeStatus, "SUPABASE_AUTH_ERROR", fallback);
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
