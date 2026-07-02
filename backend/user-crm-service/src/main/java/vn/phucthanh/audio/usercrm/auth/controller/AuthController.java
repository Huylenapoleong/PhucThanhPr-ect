package vn.phucthanh.audio.usercrm.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import vn.phucthanh.audio.shared.web.BusinessException;
import vn.phucthanh.audio.usercrm.auth.config.SupabaseAuthProperties;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.AccountResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.AuthResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.ChangeRoleRequest;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.ForgotPasswordRequest;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.LoginRequest;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.MessageResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.RegisterRequest;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.RoleResponse;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.InviteSalesRequest;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.StaffAccountResponse;
import vn.phucthanh.audio.usercrm.auth.service.AccountProvisioningService;
import vn.phucthanh.audio.usercrm.auth.service.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final AccountProvisioningService provisioningService;
    private final SupabaseAuthProperties properties;

    public AuthController(
            AuthService service,
            AccountProvisioningService provisioningService,
            SupabaseAuthProperties properties
    ) {
        this.service = service;
        this.provisioningService = provisioningService;
        this.properties = properties;
    }

    @PostMapping("/register")
    AuthResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthService.SessionResult result = service.register(
                request.email().trim().toLowerCase(),
                request.password(),
                request.displayName()
        );
        writeRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/login")
    AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthService.SessionResult result = service.login(
                request.email().trim().toLowerCase(),
                request.password()
        );
        writeRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/refresh")
    AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readRefreshCookie(request);
        AuthService.SessionResult result = service.refresh(refreshToken);
        writeRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/forgot-password")
    MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        service.requestPasswordReset(request.email().trim().toLowerCase());
        return new MessageResponse(
                "Nếu email tồn tại, hướng dẫn đặt lại mật khẩu đã được gửi"
        );
    }

    @PostMapping("/logout")
    void logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response
    ) {
        service.logout(bearerToken(authorization), UUID.fromString(jwt.getSubject()));
        clearRefreshCookie(response);
    }

    @GetMapping("/me")
    AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
    }

    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    RoleResponse changeRole(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        return service.changeRole(userId, request.role());
    }

    @PostMapping("/admin/sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    StaffAccountResponse inviteSales(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InviteSalesRequest request
    ) {
        return provisioningService.inviteSales(
                UUID.fromString(jwt.getSubject()),
                request
        );
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new BusinessException(401, "REFRESH_COOKIE_MISSING", "Không tìm thấy phiên đăng nhập");
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        401,
                        "REFRESH_COOKIE_MISSING",
                        "Không tìm thấy phiên đăng nhập"
                ));
    }

    private void writeRefreshCookie(HttpServletResponse response, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, properties.refreshCookie().maxAgeSeconds()).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(cookieName(), value)
                .httpOnly(true)
                .secure(properties.refreshCookie().secure())
                .sameSite(properties.refreshCookie().sameSite())
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    private String cookieName() {
        return properties.refreshCookie().name();
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(401, "BEARER_TOKEN_REQUIRED", "Thiếu access token");
        }
        return authorization.substring(7);
    }
}
