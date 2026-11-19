package app.locaport.files.server;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileOpsTest {

    @Test
    public void resolvesPathsInsideRoot() throws Exception {
        File root = Files.createTempDirectory("locaport").toFile();
        File resolved = FileOps.resolve(root, "/a/b/c.txt");
        assertEquals(new File(root, "a/b/c.txt").getCanonicalPath(), resolved.getCanonicalPath());
    }

    @Test
    public void resolvesRoot() throws Exception {
        File root = Files.createTempDirectory("locaport").toFile();
        assertEquals(root.getCanonicalPath(), FileOps.resolve(root, "/").getCanonicalPath());
    }

    @Test(expected = SecurityException.class)
    public void rejectsPlainTraversal() throws Exception {
        File root = Files.createTempDirectory("locaport").toFile();
        FileOps.resolve(root, "/../etc/passwd");
    }

    @Test(expected = SecurityException.class)
    public void rejectsEncodedTraversal() throws Exception {
        File root = Files.createTempDirectory("locaport").toFile();
        FileOps.resolve(root, "/%2e%2e/etc/passwd");
    }

    @Test(expected = SecurityException.class)
    public void rejectsNestedTraversal() throws Exception {
        File root = Files.createTempDirectory("locaport").toFile();
        FileOps.resolve(root, "/a/../../secret");
    }

    @Test
    public void formatsSizes() {
        assertEquals("512 B", FileOps.humanSize(512));
        assertEquals("1.0 KB", FileOps.humanSize(1024));
        assertEquals("1.5 MB", FileOps.humanSize(1024L * 1024L + 512L * 1024L));
    }

    @Test
    public void encodesSegments() {
        assertTrue(FileOps.encodeSegment("a b.txt").contains("%20"));
        assertEquals("plain.txt", FileOps.encodeSegment("plain.txt"));
    }
}
