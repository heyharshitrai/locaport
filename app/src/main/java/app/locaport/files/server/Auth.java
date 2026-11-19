package app.locaport.files.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Auth {

    private static final int MAX_FAILURES = 8;
    private static final long LOCKOUT_MS = 5 * 60 * 1000L;
    private static final String REALM = "Locaport";

    private final String username;
    private final String pin;
    private final String nonce;
    private final String opaque;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public Auth(String username, String pin) {
        this.username = username;
        this.pin = pin;
        SecureRandom random = new SecureRandom();
        this.nonce = randomHex(random, 16);
        this.opaque = randomHex(random, 8);
    }

    public static String randomPin(SecureRandom random) {
        int value = random.nextInt(1_000_000);
        return String.format(Locale.US, "%06d", value);
    }

    public String challenge(boolean secure) {
        if (secure) {
            return "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"";
        }
        return "Digest realm=\"" + REALM + "\", qop=\"auth\", algorithm=MD5, nonce=\""
                + nonce + "\", opaque=\"" + opaque + "\"";
    }

    public boolean allow(String remoteIp) {
        Attempt attempt = attempts.get(remoteIp);
        if (attempt == null) {
            return true;
        }
        if (attempt.lockedUntil > System.nanoTime() / 1_000_000L) {
            return false;
        }
        attempts.remove(remoteIp);
        return true;
    }

    public boolean verify(String authorizationHeader, String remoteIp) {
        return verify(authorizationHeader, remoteIp, "GET", true);
    }

    public boolean verify(String authorizationHeader, String remoteIp,
                          String method, boolean secure) {
        if (authorizationHeader == null) {
            return false;
        }
        boolean ok;
        if (authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            ok = verifyBasic(authorizationHeader);
        } else if (authorizationHeader.regionMatches(true, 0, "Digest ", 0, 7)) {
            ok = verifyDigest(authorizationHeader, method);
        } else {
            ok = false;
        }
        if (!ok) {
            recordFailure(remoteIp);
        } else {
            attempts.remove(remoteIp);
        }
        return ok;
    }

    private boolean verifyBasic(String header) {
        String decoded;
        try {
            byte[] raw = Base64.getDecoder().decode(header.substring(6).trim());
            decoded = new String(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String user = decoded.substring(0, colon);
        String pass = decoded.substring(colon + 1);
        return constantTimeEquals(user, username) & constantTimeEquals(pass, pin);
    }

    private boolean verifyDigest(String header, String method) {
        Map<String, String> params = parseDigest(header.substring(7).trim());
        String user = params.get("username");
        String uri = params.get("uri");
        String response = params.get("response");
        String headerNonce = params.get("nonce");
        String qop = params.get("qop");
        String nc = params.get("nc");
        String cnonce = params.get("cnonce");

        if (user == null || uri == null || response == null || headerNonce == null) {
            return false;
        }
        if (!constantTimeEquals(user, username) || !constantTimeEquals(headerNonce, nonce)) {
            return false;
        }

        String ha1 = md5(username + ":" + REALM + ":" + pin);
        String ha2 = md5(method + ":" + uri);
        String expected;
        if (qop != null && qop.toLowerCase(Locale.US).contains("auth")) {
            if (nc == null || cnonce == null) {
                return false;
            }
            expected = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
        } else {
            expected = md5(ha1 + ":" + nonce + ":" + ha2);
        }
        return constantTimeEquals(response.toLowerCase(Locale.US), expected);
    }

    private static Map<String, String> parseDigest(String input) {
        Map<String, String> params = new HashMap<>();
        int index = 0;
        while (index < input.length()) {
            int equals = input.indexOf('=', index);
            if (equals < 0) {
                break;
            }
            String key = input.substring(index, equals).trim().toLowerCase(Locale.US);
            int valueStart = equals + 1;
            while (valueStart < input.length() && input.charAt(valueStart) == ' ') {
                valueStart++;
            }
            String value;
            if (valueStart < input.length() && input.charAt(valueStart) == '"') {
                int end = input.indexOf('"', valueStart + 1);
                if (end < 0) {
                    break;
                }
                value = input.substring(valueStart + 1, end);
                index = end + 1;
            } else {
                int end = input.indexOf(',', valueStart);
                if (end < 0) {
                    end = input.length();
                }
                value = input.substring(valueStart, end).trim();
                index = end;
            }
            params.put(key, value);
            while (index < input.length()
                    && (input.charAt(index) == ',' || input.charAt(index) == ' ')) {
                index++;
            }
        }
        return params;
    }

    private void recordFailure(String remoteIp) {
        Attempt attempt = attempts.computeIfAbsent(remoteIp, k -> new Attempt());
        if (++attempt.failures >= MAX_FAILURES) {
            attempt.failures = 0;
            attempt.lockedUntil = System.nanoTime() / 1_000_000L + LOCKOUT_MS;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(x, y);
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] out = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    private static String randomHex(SecureRandom random, int bytes) {
        byte[] raw = new byte[bytes];
        random.nextBytes(raw);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : raw) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    private static final class Attempt {
        int failures;
        long lockedUntil;
    }
}
