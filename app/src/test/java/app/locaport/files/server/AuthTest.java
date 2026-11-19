package app.locaport.files.server;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthTest {

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void acceptsCorrectCredentials() {
        Auth auth = new Auth("locaport", "123456");
        assertTrue(auth.verify(basic("locaport", "123456"), "10.0.0.2"));
    }

    @Test
    public void rejectsWrongPassword() {
        Auth auth = new Auth("locaport", "123456");
        assertFalse(auth.verify(basic("locaport", "000000"), "10.0.0.3"));
    }

    @Test
    public void rejectsMalformedHeader() {
        Auth auth = new Auth("locaport", "123456");
        assertFalse(auth.verify("Bearer token", "10.0.0.4"));
        assertFalse(auth.verify(null, "10.0.0.4"));
        assertFalse(auth.verify("Basic !!!not-base64!!!", "10.0.0.4"));
    }

    @Test
    public void locksOutAfterRepeatedFailures() {
        Auth auth = new Auth("locaport", "123456");
        String bad = basic("locaport", "999999");
        for (int i = 0; i < 8; i++) {
            auth.verify(bad, "10.0.0.5");
        }
        assertFalse(auth.allow("10.0.0.5"));
        assertTrue(auth.allow("10.0.0.6"));
    }

    @Test
    public void digestAuthWorksOnPlainHttp() {
        Auth auth = new Auth("locaport", "123456");
        String challenge = auth.challenge(false);
        String nonce = between(challenge, "nonce=\"", "\"");
        String realm = "Locaport";
        String uri = "/";
        String method = "GET";
        String nc = "00000001";
        String cnonce = "abc123";
        String qop = "auth";

        String ha1 = md5("locaport:" + realm + ":123456");
        String ha2 = md5(method + ":" + uri);
        String response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);

        String header = "Digest username=\"locaport\", realm=\"" + realm
                + "\", nonce=\"" + nonce + "\", uri=\"" + uri
                + "\", response=\"" + response + "\", qop=" + qop
                + ", nc=" + nc + ", cnonce=\"" + cnonce + "\"";
        assertTrue(auth.verify(header, "10.0.0.7", method, false));
    }

    private static String between(String value, String start, String end) {
        int from = value.indexOf(start) + start.length();
        int to = value.indexOf(end, from);
        return value.substring(from, to);
    }

    private static String md5(String value) {
        try {
            byte[] out = java.security.MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void generatedPinsAreSixDigits() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 100; i++) {
            String pin = Auth.randomPin(random);
            assertTrue(pin, pin.matches("\\d{6}"));
        }
    }
}
