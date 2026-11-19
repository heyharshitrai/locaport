package app.locaport.files.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class FileOps {

    private FileOps() {
    }

    // Keep resolved paths inside the shared root.
    public static File resolve(File root, String uriPath) throws IOException {
        String path = uriPath == null ? "/" : uriPath;
        path = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        path = path.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        File candidate = new File(root, path);
        String rootCanonical = root.getCanonicalPath();
        String candidateCanonical = candidate.getCanonicalPath();
        if (!candidateCanonical.equals(rootCanonical)
                && !candidateCanonical.startsWith(rootCanonical + File.separator)) {
            throw new SecurityException("Path escapes root: " + uriPath);
        }
        return candidate;
    }

    public static String relativePath(File root, File file) throws IOException {
        String rootCanonical = root.getCanonicalPath();
        String canonical = file.getCanonicalPath();
        if (canonical.equals(rootCanonical)) {
            return "/";
        }
        String rel = canonical.substring(rootCanonical.length()).replace(File.separatorChar, '/');
        return rel.startsWith("/") ? rel : "/" + rel;
    }

    public static String mime(String name) {
        String guess = URLConnection.guessContentTypeFromName(name);
        if (guess != null) {
            return guess;
        }
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".heic")) {
            return "image/heic";
        }
        return "application/octet-stream";
    }

    public static void copy(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Cannot create " + target);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copy(child, new File(target, child.getName()));
                }
            }
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    public static boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    public static String encodeSegment(String segment) {
        try {
            return java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        } catch (Exception e) {
            return segment;
        }
    }
}
