package vn.phucthanh.audio.usercrm.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase.auth")
public record SupabaseAuthProperties(
        String baseUrl,
        String publishableKey,
        String secretKey,
        String emailRedirectUrl,
        BootstrapAdmin bootstrapAdmin,
        RefreshCookie refreshCookie
) {
    public record BootstrapAdmin(
            String email,
            String displayName
    ) {
    }

    public record RefreshCookie(
            String name,
            boolean secure,
            String sameSite,
            long maxAgeSeconds
    ) {
    }
}
