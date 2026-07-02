package vn.phucthanh.audio.shared.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email,
        Set<String> roles
) {
    public AuthenticatedUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
