package vn.phucthanh.audio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SupabaseJwtAuthenticationConverterTest {

    private final SupabaseJwtAuthenticationConverter converter =
            new SupabaseJwtAuthenticationConverter();

    @Test
    void readsRolesFromHookAndAppMetadataButNotUserMetadata() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("app_metadata", Map.of("roles", List.of("sales", "warehouse-manager")))
                .claim("user_role", "customer")
                .claim("user_metadata", Map.of("roles", List.of("admin")))
                .build();

        var authentication = converter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_SALES", "ROLE_WAREHOUSE_MANAGER", "ROLE_CUSTOMER")
                .doesNotContain("ROLE_ADMIN");
    }
}
