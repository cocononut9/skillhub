package com.iflytek.skillhub.infra.scanner;

import com.iflytek.skillhub.domain.skill.metadata.WebResourceMetadataParser;
import com.iflytek.skillhub.domain.skill.metadata.PromptResourceMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Adds a scanner-only manifest to a staged website or prompt package; never modifies the stored ZIP. */
final class WebResourceScanPackage implements AutoCloseable {
    private final Path scanPath;
    private final Path temporaryPath;

    private WebResourceScanPackage(Path scanPath, Path temporaryPath) {
        this.scanPath = scanPath;
        this.temporaryPath = temporaryPath;
    }

    static WebResourceScanPackage prepare(Path path) throws IOException {
        if (!Files.exists(path)) return new WebResourceScanPackage(path, null);
        if (Files.isDirectory(path)) {
            Path manifest = path.resolve("SKILL.md");
            Path readme = path.resolve("README.md");
            if (Files.exists(manifest) || !Files.isRegularFile(readme)) return new WebResourceScanPackage(path, null);
            byte[] content = Files.readAllBytes(readme);
            if (!isReadmeResource(content)) return new WebResourceScanPackage(path, null);
            Files.write(manifest, manifest(content), StandardOpenOption.CREATE_NEW);
            return new WebResourceScanPackage(path, manifest);
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry readme = zip.getEntry("README.md");
            if (zip.getEntry("SKILL.md") != null || readme == null) return new WebResourceScanPackage(path, null);
            byte[] content;
            try (var input = zip.getInputStream(readme)) {
                content = input.readNBytes(10 * 1024 * 1024 + 1);
            }
            if (content.length > 10 * 1024 * 1024) throw new IOException("README exceeds scan size limit");
            if (!isReadmeResource(content)) return new WebResourceScanPackage(path, null);
            Path adapted = Files.createTempFile(path.getParent(), "web-scan-", ".zip");
            try {
                try (var output = new ZipOutputStream(Files.newOutputStream(adapted))) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        output.putNextEntry(new ZipEntry(entry.getName()));
                        try (var input = zip.getInputStream(entry)) { input.transferTo(output); }
                        output.closeEntry();
                    }
                    output.putNextEntry(new ZipEntry("SKILL.md"));
                    output.write(manifest(content));
                    output.closeEntry();
                }
                return new WebResourceScanPackage(adapted, adapted);
            } catch (Exception e) {
                Files.deleteIfExists(adapted);
                throw e;
            }
        }
    }

    private static boolean isReadmeResource(byte[] readme) {
        if (new PromptResourceMetadataParser().parseReadme(new String(readme, StandardCharsets.UTF_8)).isPresent()) return true;
        return new WebResourceMetadataParser().parse(List.of(
                new PackageEntry("README.md", readme, readme.length, "text/markdown"))).isPresent();
    }

    private static byte[] manifest(byte[] readme) {
        if (new PromptResourceMetadataParser().parseReadme(new String(readme, StandardCharsets.UTF_8)).isPresent()) {
            return ("---\nname: prompt-resource\ndescription: Prompt documentation and full text security review.\n---\n"
                    + new String(readme, StandardCharsets.UTF_8) + "\n\n[提示词正文](PROMPT.md)\n").getBytes(StandardCharsets.UTF_8);
        }
        return ("---\nname: website-resource\ndescription: Website resource documentation for security review.\n---\n"
                + new String(readme, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }

    Path path() { return scanPath; }

    @Override
    public void close() throws IOException {
        if (temporaryPath != null) Files.deleteIfExists(temporaryPath);
    }
}
