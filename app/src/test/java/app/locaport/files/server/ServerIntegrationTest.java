package app.locaport.files.server;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end: boots the real server on a loopback port and speaks raw HTTP,
 * because HttpURLConnection refuses WebDAV methods.
 */
public class ServerIntegrationTest {

    private File root;
    private LocaportServer server;
    private String authHeader;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("locaport-it").toFile();
        Files.write(new File(root, "hello.txt").toPath(), "hello world".getBytes(StandardCharsets.UTF_8));
        File folder = new File(root, "Photos");
        assertTrue(folder.mkdirs());

        Auth auth = new Auth("locaport", "123456");
        server = new LocaportServer("127.0.0.1", root, 0, auth, false, null, false, "Test Phone");
        server.startServer();
        authHeader = "Basic " + Base64.getEncoder()
                .encodeToString("locaport:123456".getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
        FileOps.deleteRecursive(root);
    }

    @Test
    public void refusesWithoutCredentials() throws Exception {
        String response = request("GET", "/", null, false, null);
        assertTrue(response, response.startsWith("HTTP/1.1 401"));
        assertTrue(response, response.contains("WWW-Authenticate"));
    }

    @Test
    public void acceptsWithCredentials() throws Exception {
        String response = request("GET", "/", null, true, null);
        assertTrue(response, response.startsWith("HTTP/1.1 200"));
        assertTrue(response, response.contains("hello.txt"));
        assertTrue(response, response.contains("Photos"));
    }

    @Test
    public void uploadsThroughPut() throws Exception {
        byte[] payload = "uploaded through webdav".getBytes(StandardCharsets.UTF_8);
        String response = request("PUT", "/upload.txt", payload, true, null);
        assertTrue(response, response.startsWith("HTTP/1.1 201"));
        assertArrayEquals(payload, Files.readAllBytes(new File(root, "upload.txt").toPath()));
    }

    @Test
    public void uploadsWithHttpUrlConnection() throws Exception {
        byte[] payload = "uploaded with HttpURLConnection".getBytes(StandardCharsets.UTF_8);
        java.net.URL url = new java.net.URL(
                "http://127.0.0.1:" + server.getListeningPort() + "/hc.txt");
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Authorization", authHeader);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }
        int code = connection.getResponseCode();
        assertEquals(201, code);
        assertArrayEquals(payload, Files.readAllBytes(new File(root, "hc.txt").toPath()));
    }

    @Test
    public void downloadsWithCorrectBytes() throws Exception {
        String response = request("GET", "/hello.txt", null, true, null);
        assertTrue(response, response.startsWith("HTTP/1.1 200"));
        assertTrue(response, response.contains("hello world"));
    }

    @Test
    public void propfindReturnsMultiStatus() throws Exception {
        String response = request("PROPFIND", "/", null, true, "Depth: 1\r\n");
        assertTrue(response, response.startsWith("HTTP/1.1 207"));
        assertTrue(response, response.contains("<D:multistatus"));
        assertTrue(response, response.contains("hello.txt"));
        assertTrue(response, response.contains("<D:collection/>"));
    }

    @Test
    public void mkcolCreatesDirectory() throws Exception {
        String response = request("MKCOL", "/NewFolder", null, true, null);
        assertTrue(response, response.startsWith("HTTP/1.1 201"));
        assertTrue(new File(root, "NewFolder").isDirectory());
    }

    @Test
    public void blocksTraversal() throws Exception {
        String response = request("GET", "/../etc/passwd", null, true, null);
        assertTrue(response, response.startsWith("HTTP/1.1 403"));
    }

    @Test
    public void readOnlyRefusesWrites() throws Exception {
        server.setReadOnly(true);
        String response = request("PUT", "/blocked.txt", "no".getBytes(StandardCharsets.UTF_8), true, null);
        assertTrue(response, response.startsWith("HTTP/1.1 403"));
        assertEquals(false, new File(root, "blocked.txt").exists());
    }

    private String request(String method, String path, byte[] body, boolean authenticated,
                           String extraHeaders) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        head.append("Host: 127.0.0.1\r\n");
        if (authenticated) {
            head.append("Authorization: ").append(authHeader).append("\r\n");
        }
        if (extraHeaders != null) {
            head.append(extraHeaders);
        }
        if (body != null) {
            head.append("Content-Length: ").append(body.length).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");

        try (Socket socket = new Socket("127.0.0.1", server.getListeningPort());
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {
            out.write(head.toString().getBytes(StandardCharsets.UTF_8));
            if (body != null) {
                out.write(body);
            }
            out.flush();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
