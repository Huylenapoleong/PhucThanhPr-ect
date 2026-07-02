package vn.phucthanh.audio.shared.domain;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class BusinessCodes {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private BusinessCodes() {
    }

    public static String next(String prefix) {
        String safePrefix = prefix.trim().toUpperCase(Locale.ROOT);
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return safePrefix + "-" + LocalDateTime.now(Clock.systemUTC()).format(TIME) + "-" + suffix;
    }
}
