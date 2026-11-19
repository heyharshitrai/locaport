package app.locaport.files.server;

import fi.iki.elonen.NanoHTTPD;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import javax.net.ssl.SSLContext;

// HTTP/HTTPS + WebDAV server rooted at shared storage.
public final class LocaportServer extends NanoHTTPD {

    private static final String MIME_XML = "application/xml; charset=utf-8";
    private static final String MIME_HTML = "text/html; charset=utf-8";

    private final File root;
    private final Auth auth;

    private volatile boolean readOnly;
    private volatile long lastRequestAt = System.currentTimeMillis();
    private final SSLContext sslContext;
    private final boolean secure;
    private final String deviceName;

    public LocaportServer(String host, File root, int port, Auth auth, boolean readOnly,
                          SSLContext sslContext, boolean secure, String deviceName) {
        super(host, port);
        this.root = root;
        this.auth = auth;
        this.readOnly = readOnly;
        this.sslContext = sslContext;
        this.secure = secure;
        this.deviceName = deviceName == null || deviceName.trim().isEmpty()
                ? "Android"
                : deviceName.trim();
    }

    public void startServer() throws IOException {
        if (sslContext != null) {
            makeSecure(sslContext.getServerSocketFactory(), null);
        }
        start(SOCKET_READ_TIMEOUT, false);
    }

    public long idleMillis() {
        return System.currentTimeMillis() - lastRequestAt;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public Response serve(IHTTPSession session) {
        lastRequestAt = System.currentTimeMillis();

        // Unread bodies (PROPFIND, PROPPATCH, LOCK) poison the next keep-alive
        // request, so drain everything we don't consume.
        if (session.getMethod() != Method.PUT) {
            drainBody(session);
        }

        String remoteIp = session.getRemoteIpAddress();
        if (!auth.allow(remoteIp)) {
            return message(Response.Status.FORBIDDEN, "Too many failed attempts. Try again in a few minutes.");
        }
        if (!auth.verify(session.getHeaders().get("authorization"), remoteIp,
                session.getMethod().name(), secure)) {
            Response response = message(Response.Status.UNAUTHORIZED, "Authentication required.");
            response.addHeader("WWW-Authenticate", auth.challenge(secure));
            return response;
        }

        try {
            File target = FileOps.resolve(root, session.getUri());
            Method method = session.getMethod();
            switch (method) {
                case OPTIONS:
                    return options();
                case PROPFIND:
                    return propfind(session, target);
                case PROPPATCH:
                    return proppatch(target);
                case GET:
                case HEAD:
                    return get(session, target);
                case PUT:
                    return put(session, target);
                case DELETE:
                    return delete(target);
                case MKCOL:
                    return mkcol(target);
                case COPY:
                    return copyOrMove(session, target, false);
                case MOVE:
                    return copyOrMove(session, target, true);
                case LOCK:
                    return lock();
                case UNLOCK:
                    return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "");
                default:
                    return message(Response.Status.METHOD_NOT_ALLOWED, "Method not allowed.");
            }
        } catch (SecurityException e) {
            return message(Response.Status.FORBIDDEN, "Path is outside the shared storage.");
        } catch (Exception e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return message(Response.Status.INTERNAL_ERROR, detail);
        }
    }

    private Response options() {
        Response response = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
        response.addHeader("DAV", "1,2");
        response.addHeader("MS-Author-Via", "DAV");
        response.addHeader("Allow", "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, PROPPATCH, MKCOL, COPY, MOVE, LOCK, UNLOCK");
        return response;
    }

    private Response get(IHTTPSession session, File target) throws IOException {
        if (!target.exists()) {
            return message(Response.Status.NOT_FOUND, "Not found.");
        }
        if (target.isDirectory()) {
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML,
                    WebUi.render(root, target, readOnly, deviceName));
        }

        String mime = FileOps.mime(target.getName());
        long length = target.length();
        String range = session.getHeaders().get("range");

        if (range != null && range.startsWith("bytes=") && length > 0) {
            long[] bounds = parseRange(range, length);
            long start = bounds[0];
            long end = bounds[1];
            long count = end - start + 1;
            FileInputStream in = new FileInputStream(target);
            long skipped = 0;
            while (skipped < start) {
                long step = in.skip(start - skipped);
                if (step <= 0) {
                    break;
                }
                skipped += step;
            }
            Response response = newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT, mime, new BoundedInputStream(in, count), count);
            response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
            response.addHeader("Accept-Ranges", "bytes");
            return response;
        }

        Response response = newFixedLengthResponse(Response.Status.OK, mime, new FileInputStream(target), length);
        response.addHeader("Accept-Ranges", "bytes");
        return response;
    }

    private Response put(IHTTPSession session, File target) throws IOException {
        if (readOnly) {
            drainBody(session);
            return message(Response.Status.FORBIDDEN, "Read-only mode is on.");
        }
        if (target.isDirectory()) {
            drainBody(session);
            return message(Response.Status.CONFLICT, "Target is a directory.");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            drainBody(session);
            return message(Response.Status.INTERNAL_ERROR, "Cannot create parent directory.");
        }

        long declared = -1;
        String contentLength = session.getHeaders().get("content-length");
        if (contentLength != null) {
            try {
                declared = Long.parseLong(contentLength.trim());
            } catch (NumberFormatException ignored) {
                declared = -1;
            }
        }

        // Closing this stream closes the socket before NanoHTTPD writes the response.
        InputStream in = session.getInputStream();
        try (OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
                if (declared > 0 && total >= declared) {
                    break;
                }
            }
        }
        return newFixedLengthResponse(Response.Status.CREATED, "text/plain", "");
    }

    private static void drainBody(IHTTPSession session) {
        String header = session.getHeaders().get("content-length");
        if (header == null) {
            return;
        }
        long length;
        try {
            length = Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (length <= 0) {
            return;
        }
        InputStream in = session.getInputStream();
        byte[] buffer = new byte[8192];
        long remaining = length;
        try {
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                remaining -= read;
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    private Response delete(File target) {
        if (readOnly) {
            return message(Response.Status.FORBIDDEN, "Read-only mode is on.");
        }
        if (!target.exists()) {
            return message(Response.Status.NOT_FOUND, "Not found.");
        }
        if (target.equals(root)) {
            return message(Response.Status.FORBIDDEN, "Refusing to delete the shared root.");
        }
        return FileOps.deleteRecursive(target)
                ? newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
                : message(Response.Status.INTERNAL_ERROR, "Delete failed.");
    }

    private Response mkcol(File target) {
        if (readOnly) {
            return message(Response.Status.FORBIDDEN, "Read-only mode is on.");
        }
        if (target.exists()) {
            return message(Response.Status.METHOD_NOT_ALLOWED, "Already exists.");
        }
        return target.mkdirs()
                ? newFixedLengthResponse(Response.Status.CREATED, "text/plain", "")
                : message(Response.Status.INTERNAL_ERROR, "Could not create directory.");
    }

    private Response copyOrMove(IHTTPSession session, File source, boolean move) throws IOException {
        if (readOnly) {
            return message(Response.Status.FORBIDDEN, "Read-only mode is on.");
        }
        String destinationHeader = session.getHeaders().get("destination");
        if (destinationHeader == null || destinationHeader.isEmpty()) {
            return message(Response.Status.BAD_REQUEST, "Destination header is required.");
        }
        String destinationPath;
        try {
            destinationPath = URI.create(destinationHeader).getPath();
        } catch (Exception e) {
            return message(Response.Status.BAD_REQUEST, "Destination header is not a URL.");
        }
        File destination = FileOps.resolve(root, destinationPath);
        if (!source.exists()) {
            return message(Response.Status.NOT_FOUND, "Source not found.");
        }
        if (source.equals(root)) {
            return message(Response.Status.FORBIDDEN, "Refusing to move the shared root.");
        }
        if (destination.exists()) {
            String overwrite = session.getHeaders().get("overwrite");
            if (overwrite != null && overwrite.equalsIgnoreCase("F")) {
                return message(Response.Status.PRECONDITION_FAILED, "Destination exists.");
            }
            FileOps.deleteRecursive(destination);
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return message(Response.Status.INTERNAL_ERROR, "Cannot create destination directory.");
        }
        if (move) {
            if (!source.renameTo(destination)) {
                FileOps.copy(source, destination);
                FileOps.deleteRecursive(source);
            }
        } else {
            FileOps.copy(source, destination);
        }
        return newFixedLengthResponse(Response.Status.CREATED, "text/plain", "");
    }

    private Response propfind(IHTTPSession session, File target) throws IOException {
        String depth = session.getHeaders().get("depth");
        boolean includeChildren = depth == null || !depth.trim().equals("0");

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        xml.append("<D:multistatus xmlns:D=\"DAV:\">");
        appendPropResponse(xml, target);
        if (includeChildren && target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    appendPropResponse(xml, child);
                }
            }
        }
        xml.append("</D:multistatus>");

        Response response = newFixedLengthResponse(Response.Status.MULTI_STATUS, MIME_XML, xml.toString());
        response.addHeader("DAV", "1,2");
        return response;
    }

    private void appendPropResponse(StringBuilder xml, File file) throws IOException {
        String href = FileOps.relativePath(root, file);
        xml.append("<D:response><D:href>").append(escapeXml(encodeHref(href))).append("</D:href>");
        xml.append("<D:propstat><D:prop>");
        xml.append("<D:displayname>")
                .append(escapeXml("/".equals(href) ? "Shared storage" : file.getName()))
                .append("</D:displayname>");
        if (file.isDirectory()) {
            xml.append("<D:resourcetype><D:collection/></D:resourcetype>");
        } else {
            xml.append("<D:resourcetype/>");
            xml.append("<D:getcontentlength>").append(file.length()).append("</D:getcontentlength>");
            xml.append("<D:getcontenttype>")
                    .append(escapeXml(FileOps.mime(file.getName())))
                    .append("</D:getcontenttype>");
        }
        xml.append("<D:getlastmodified>").append(httpDate(file.lastModified())).append("</D:getlastmodified>");
        xml.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>");
    }

    private Response proppatch(File target) throws IOException {
        String href = encodeHref(FileOps.relativePath(root, target));
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<D:multistatus xmlns:D=\"DAV:\"><D:response><D:href>" + escapeXml(href)
                + "</D:href><D:propstat><D:prop/><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>";
        return newFixedLengthResponse(Response.Status.MULTI_STATUS, MIME_XML, body);
    }

    private Response lock() {
        String token = "opaquelocktoken:" + UUID.randomUUID();
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<D:prop xmlns:D=\"DAV:\"><D:lockdiscovery><D:activelock>"
                + "<D:locktype><D:write/></D:locktype>"
                + "<D:lockscope><D:exclusive/></D:lockscope>"
                + "<D:depth>infinity</D:depth>"
                + "<D:owner><D:href>locaport</D:href></D:owner>"
                + "<D:timeout>Second-3600</D:timeout>"
                + "<D:locktoken><D:href>" + token + "</D:href></D:locktoken>"
                + "</D:activelock></D:lockdiscovery></D:prop>";
        Response response = newFixedLengthResponse(Response.Status.OK, MIME_XML, body);
        response.addHeader("Lock-Token", "<" + token + ">");
        return response;
    }

    private static String encodeHref(String path) {
        String[] parts = path.split("/", -1);
        StringBuilder out = new StringBuilder(path.length() + 16);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(FileOps.encodeSegment(parts[i]));
        }
        String encoded = out.toString();
        return encoded.startsWith("/") ? encoded : "/" + encoded;
    }

    private static long[] parseRange(String header, long length) {
        String spec = header.substring(6).trim();
        int dash = spec.indexOf('-');
        long start = 0;
        long end = length - 1;
        try {
            if (dash > 0) {
                start = Long.parseLong(spec.substring(0, dash).trim());
                String tail = spec.substring(dash + 1).trim();
                if (!tail.isEmpty()) {
                    end = Long.parseLong(tail);
                }
            } else if (dash == 0) {
                long suffix = Long.parseLong(spec.substring(1).trim());
                start = Math.max(0, length - suffix);
            }
        } catch (NumberFormatException ignored) {
            start = 0;
            end = length - 1;
        }
        if (start < 0) {
            start = 0;
        }
        if (end >= length) {
            end = length - 1;
        }
        if (start > end) {
            start = 0;
            end = Math.min(length - 1, 0);
        }
        return new long[]{start, end};
    }

    private static Response message(Response.Status status, String text) {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", text);
    }

    private static String httpDate(long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date(epochMillis));
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int allowed = (int) Math.min(length, remaining);
            int read = super.read(buffer, offset, allowed);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            long skipped = super.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }
    }
}
