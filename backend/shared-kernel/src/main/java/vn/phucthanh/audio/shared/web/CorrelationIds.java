package vn.phucthanh.audio.shared.web;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    public static final String HEADER_NAME = "X-Correlation-Id";
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    private CorrelationIds() {
    }

    public static String normalizeOrCreate(String candidate) {
        if (candidate != null && SAFE_VALUE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
