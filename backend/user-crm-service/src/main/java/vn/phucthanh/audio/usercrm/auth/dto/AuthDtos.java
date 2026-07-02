package vn.phucthanh.audio.usercrm.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {
    }

    public record ChangeRoleRequest(
            @NotBlank
            @Pattern(regexp = "ADMIN|SALES|CUSTOMER")
            String role
    ) {
    }

    public record InviteSalesRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 150) String displayName,
            @Size(max = 30) String phone,
            @Size(max = 150) String positionTitle
    ) {
    }

    public record StaffAccountResponse(
            UUID userId,
            UUID staffId,
            String staffCode,
            String email,
            String displayName,
            String role,
            String accountType,
            String status,
            boolean invitationSent
    ) {
    }

    public record MessageResponse(String message) {
    }

    public record RoleResponse(
            UUID id,
            String role,
            long sessionVersion
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            AccountResponse account,
            boolean emailConfirmationRequired
    ) {
    }

    public record AccountResponse(
            UUID id,
            String email,
            String accountType,
            String role,
            String status,
            String displayName,
            String avatarUrl,
            String locale,
            String timeZone,
            boolean mfaRequired,
            long sessionVersion
    ) {
    }

    public record SupabaseTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            SupabaseUser user
    ) {
    }

    public record SupabaseSignupResponse(
            SupabaseUser user,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken
    ) {
    }

    public record SupabaseUser(
            UUID id,
            String email,
            @JsonProperty("user_metadata") Map<String, Object> userMetadata,
            @JsonProperty("app_metadata") Map<String, Object> appMetadata
    ) {
    }
}
