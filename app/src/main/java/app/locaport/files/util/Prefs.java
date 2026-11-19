package app.locaport.files.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

public final class Prefs {

    public static final int DEFAULT_PORT = 8443;
    private static final String NAME = "locaport";

    private Prefs() {
    }

    private static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int port(Context ctx) {
        return get(ctx).getInt("port", DEFAULT_PORT);
    }

    public static void setPort(Context ctx, int port) {
        get(ctx).edit().putInt("port", port).apply();
    }

    public static boolean readOnly(Context ctx) {
        return get(ctx).getBoolean("read_only", false);
    }

    public static void setReadOnly(Context ctx, boolean value) {
        get(ctx).edit().putBoolean("read_only", value).apply();
    }

    public static boolean autoStop(Context ctx) {
        return get(ctx).getBoolean("auto_stop", true);
    }

    public static void setAutoStop(Context ctx, boolean value) {
        get(ctx).edit().putBoolean("auto_stop", value).apply();
    }

    public static boolean stopOnLock(Context ctx) {
        return get(ctx).getBoolean("stop_on_lock", false);
    }

    public static void setStopOnLock(Context ctx, boolean value) {
        get(ctx).edit().putBoolean("stop_on_lock", value).apply();
    }

    public static boolean httpCompat(Context ctx) {
        return get(ctx).getBoolean("http_compat", true);
    }

    public static void setHttpCompat(Context ctx, boolean value) {
        get(ctx).edit().putBoolean("http_compat", value).apply();
    }

    public static String username(Context ctx) {
        return sanitizeUsername(get(ctx).getString("username", null));
    }

    public static void setUsername(Context ctx, String value) {
        get(ctx).edit().putString("username", sanitizeUsername(value)).apply();
    }

    private static String sanitizeUsername(String value) {
        if (value == null) {
            return "locaport";
        }
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9._-]", "");
        if (cleaned.isEmpty()) {
            return "locaport";
        }
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    public static boolean fixedPinEnabled(Context ctx) {
        return get(ctx).getBoolean("fixed_pin_enabled", false);
    }

    public static void setFixedPinEnabled(Context ctx, boolean value) {
        get(ctx).edit().putBoolean("fixed_pin_enabled", value).apply();
    }

    /** Six digits, or empty when not set. */
    public static String fixedPin(Context ctx) {
        String value = get(ctx).getString("fixed_pin", "");
        return value != null && value.matches("\\d{6}") ? value : "";
    }

    public static void setFixedPin(Context ctx, String value) {
        get(ctx).edit().putString("fixed_pin", value == null ? "" : value).apply();
    }

    public static String deviceName(Context ctx) {
        return sanitize(get(ctx).getString("device_name", null), defaultDeviceName(), 40);
    }

    public static void setDeviceName(Context ctx, String value) {
        get(ctx).edit().putString("device_name", sanitize(value, defaultDeviceName(), 40)).apply();
    }

    public static String defaultDeviceName() {
        String model = android.os.Build.MODEL;
        if (model == null || model.trim().isEmpty()) {
            return "Android";
        }
        return model.trim();
    }

    private static String sanitize(String value, String fallback, int max) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim().replaceAll("\\p{Cntrl}", "");
        if (trimmed.isEmpty()) {
            return fallback;
        }
        if (trimmed.length() > max) {
            trimmed = trimmed.substring(0, max).trim();
        }
        return trimmed;
    }

    public static int httpPort(Context ctx) {
        return get(ctx).getInt("http_port", 8080);
    }

    public static void setHttpPort(Context ctx, int port) {
        get(ctx).edit().putInt("http_port", port).apply();
    }

    public static char[] tlsPassword(Context ctx) {
        String stored = get(ctx).getString("tls_password", null);
        if (stored == null) {
            byte[] raw = new byte[24];
            new SecureRandom().nextBytes(raw);
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            stored = sb.toString();
            get(ctx).edit().putString("tls_password", stored).apply();
        }
        return stored.toCharArray();
    }
}
