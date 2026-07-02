package vn.phucthanh.audio.shared.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class SupabaseJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopesConverter =
            new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Collection<GrantedAuthority> scopes = scopesConverter.convert(jwt);
        if (scopes != null) {
            authorities.addAll(scopes);
        }

        Object appMetadataClaim = jwt.getClaim("app_metadata");
        if (appMetadataClaim instanceof Map<?, ?> appMetadata) {
            addRole(authorities, appMetadata.get("role"));
            addRoles(authorities, appMetadata.get("roles"));
        }
        addRole(authorities, jwt.getClaim("user_role"));

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static void addRoles(Set<GrantedAuthority> authorities, Object rolesClaim) {
        if (rolesClaim instanceof Collection<?> roles) {
            roles.forEach(role -> addRole(authorities, role));
        } else {
            addRole(authorities, rolesClaim);
        }
    }

    private static void addRole(Set<GrantedAuthority> authorities, Object roleClaim) {
        if (!(roleClaim instanceof String role) || role.isBlank()) {
            return;
        }

        String normalized = role.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        authorities.add(new SimpleGrantedAuthority("ROLE_" + normalized));
    }
}
