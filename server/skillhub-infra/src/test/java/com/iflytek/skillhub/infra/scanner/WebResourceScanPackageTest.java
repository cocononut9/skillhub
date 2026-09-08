package com.iflytek.skillhub.infra.scanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WebResourceScanPackageTest {
    @TempDir Path directory;
    private final String readme = "# 网页工具\n\n> 测试网页。\n\n资源类型：网页\n使用入口：https://example.com\n";
    @Test
    void adaptsAndCleansDirectoryWithoutChangingReadme() throws Exception {
        Files.writeString(directory.resolve("README.md"), readme);
        try (var prepared = WebResourceScanPackage.prepare(directory)) {
            assertEquals(directory, prepared.path());
            assertTrue(Files.readString(directory.resolve("SKILL.md")).contains(readme));
        }
        assertFalse(Files.exists(directory.resolve("SKILL.md")));
        assertEquals(readme, Files.readString(directory.resolve("README.md")));
    }
    @Test
    void adaptsZipOnlyForScanAndPreservesOriginal() throws Exception {
        Path original = directory.resolve("bundle.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(original))) {
            output.putNextEntry(new ZipEntry("README.md"));
            output.write(readme.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        byte[] originalBytes = Files.readAllBytes(original);
        Path adapted;
        try (var prepared = WebResourceScanPackage.prepare(original)) {
            adapted = prepared.path();
            try (var zip = new ZipFile(adapted.toFile())) {
                assertNotNull(zip.getEntry("SKILL.md"));
                assertNotNull(zip.getEntry("README.md"));
            }
        }
        assertFalse(Files.exists(adapted));
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
    }
}
