package com.iflytek.skillhub.infra.scanner;

import com.iflytek.skillhub.domain.skill.metadata.PluginResourceMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Expands plugin installers into a bounded, scanner-only copy; originals are never changed. */
final class PluginResourceScanPackage implements AutoCloseable {
    private static final int MAX_DEPTH = 4;
    private static final int MAX_FILES = 500;
    private static final int MAX_TOTAL = 100 * 1024 * 1024;
    // Upstream loader ignores text files whose size is >= 10MB.
    private static final int MAX_TEXT = 10 * 1024 * 1024 - 1;
    private final Path path;
    private final boolean temporary;
    private Map<String, String> originalPaths = Map.of();

    private PluginResourceScanPackage(Path path, boolean temporary) {
        this.path = path;
        this.temporary = temporary;
    }

    static PluginResourceScanPackage prepare(Path source) throws IOException {
        if (!Files.exists(source)) return new PluginResourceScanPackage(source, false);
        byte[] readme;
        byte[] installer;
        String installerName;
        if (Files.isDirectory(source)) {
            Path file = source.resolve("README.md");
            if (!Files.isRegularFile(file)) return new PluginResourceScanPackage(source, false);
            try (var input = Files.newInputStream(file)) { readme = bounded(input, MAX_TEXT); }
            var metadata = new PluginResourceMetadataParser().parseReadme(new String(readme, StandardCharsets.UTF_8));
            if (metadata.isEmpty()) return new PluginResourceScanPackage(source, false);
            installerName = (String) metadata.get().frontmatter().get("installerFile");
            Path installerPath = source.resolve(installerName);
            if (Files.isSymbolicLink(installerPath)) throw new IOException("Plugin installer cannot be a symbolic link");
            try (var input = Files.newInputStream(installerPath)) { installer = bounded(input, MAX_TOTAL); }
        } else {
            try (ZipFile zip = new ZipFile(source.toFile())) {
                ZipEntry entry = zip.getEntry("README.md");
                if (entry == null) return new PluginResourceScanPackage(source, false);
                try (var input = zip.getInputStream(entry)) { readme = bounded(input, MAX_TEXT); }
                var metadata = new PluginResourceMetadataParser().parseReadme(new String(readme, StandardCharsets.UTF_8));
                if (metadata.isEmpty()) return new PluginResourceScanPackage(source, false);
                installerName = (String) metadata.get().frontmatter().get("installerFile");
                ZipEntry file = zip.getEntry(installerName);
                if (file == null) throw new IOException("Plugin installer is missing");
                try (var input = zip.getInputStream(file)) { installer = bounded(input, MAX_TOTAL); }
            }
        }
        ScanTree tree = new ScanTree();
        tree.expand(installer, installerName, 0);
        if (tree.files.isEmpty()) throw new IOException("Plugin installer contains no scannable files");
        String manifest = "---\nname: plugin-resource\ndescription: Plugin package security review.\n---\n"
                + new String(readme, StandardCharsets.UTF_8) + "\n\n## Plugin files for security review\n"
                + String.join("\n", tree.references) + "\n";
        if (manifest.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT) throw new IOException("Plugin scan manifest exceeds 10MB limit");
        tree.files.put("README.md", readme);
        tree.files.put("SKILL.md", manifest.getBytes(StandardCharsets.UTF_8));
        Path target = Files.isDirectory(source)
                ? Files.createTempDirectory(source.getParent(), "plugin-scan-")
                : Files.createTempFile(source.getParent(), "plugin-scan-", ".zip");
        var result = new PluginResourceScanPackage(target, true);
        result.originalPaths = Map.copyOf(tree.originalPaths);
        try {
            if (Files.isDirectory(target)) {
                for (var entry : tree.files.entrySet()) {
                    Path file = target.resolve(entry.getKey());
                    Files.createDirectories(file.getParent());
                    Files.write(file, entry.getValue());
                }
            } else {
                try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
                    for (var entry : tree.files.entrySet()) {
                        zip.putNextEntry(new ZipEntry(entry.getKey()));
                        zip.write(entry.getValue());
                        zip.closeEntry();
                    }
                }
            }
            return result;
        } catch (IOException e) {
            result.close();
            throw e;
        }
    }

    private static final class ScanTree {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        final List<String> references = new ArrayList<>();
        final Map<String, String> originalPaths = new LinkedHashMap<>();
        long total;
        int count;

        void expand(byte[] archive, String origin, int depth) throws IOException {
            if (depth > MAX_DEPTH) throw new IOException("Plugin archive nesting exceeds 4 levels: " + origin);
            byte[] zipBytes = unwrapCrx(archive);
            if (!isZip(zipBytes)) throw new IOException("Installer is not a supported ZIP/CRX/XPI/VSIX archive: " + origin);
            var names = new HashSet<String>();
            int entries = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name;
                    try {
                        name = SkillPackagePolicy.normalizeEntryPath(entry.isDirectory()
                                ? entry.getName().replaceAll("/+$", "") : entry.getName());
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Unsafe plugin archive path: " + entry.getName(), e);
                    }
                    if (!names.add(name)) throw new IOException("Duplicate plugin archive path: " + name);
                    if (++count > MAX_FILES) throw new IOException("Plugin scan exceeds 500 archive entries");
                    if (entry.isDirectory()) { zip.closeEntry(); continue; }
                    entries++;
                    byte[] content = bounded(zip, (int) Math.max(0, MAX_TOTAL - total));
                    total += content.length;
                    String sourcePath = origin + "/" + name;
                    if (isZip(content) || isCrx(content)) {
                        expand(content, sourcePath, depth + 1);
                    } else if (PluginResourceMetadataParser.isInstallerFile(name)
                            || name.toLowerCase(Locale.ROOT).matches(".*\\.(exe|dll|so|dylib|wasm|msi|dmg|pkg|tgz|gz|7z|rar|jar)$")) {
                        throw new IOException("Cannot inspect embedded binary or archive: " + sourcePath);
                    } else {
                        addFile(sourcePath, name, content);
                    }
                    zip.closeEntry();
                }
            }
            if (entries == 0) throw new IOException("Empty or invalid plugin archive: " + origin);
        }

        void addFile(String origin, String name, byte[] content) throws IOException {
            String basename = name.substring(name.lastIndexOf('/') + 1);
            String safeName = basename.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeName.startsWith(".")) safeName = "hidden" + safeName;
            String scanName = "plugin/" + count + "/" + safeName;
            originalPaths.put(scanName, origin);
            if (isText(content)) {
                total += content.length;
                if (total > MAX_TOTAL) throw new IOException("Plugin scan copy exceeds 100MB limit");
                if (content.length > MAX_TEXT) throw new IOException("Plugin text file exceeds scanner 10MB limit: " + origin);
                files.put(scanName, content);
                // Upstream reference rules only cover md/py/sh. Retain native files and also
                // scan every text file through a referenced Markdown view with identical bytes.
                references.add("- [" + origin.replaceAll("[\\[\\]()\\r\\n]", "_") + "](" + scanName + ")");
                String textView = "plugin-text/" + count + ".md";
                files.put(textView, content);
                originalPaths.put(textView, origin);
                references.add("- [Text scan: " + origin.replaceAll("[\\[\\]()\\r\\n]", "_") + "](" + textView + ")");
            } else if (isImageAsset(name, content)) {
                files.put(scanName, content);
            } else {
                throw new IOException("Cannot statically inspect binary plugin file: " + origin);
            }
        }
    }

    private static boolean isText(byte[] content) {
        for (byte value : content) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) { return false; }
    }

    private static boolean isImageAsset(String name, byte[] content) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ico")) return content.length >= 6 && content[0] == 0 && content[1] == 0
                && content[2] == 1 && content[3] == 0;
        if (!lower.matches(".*\\.(png|jpg|jpeg|gif|webp)$")) return false;
        return SkillPackagePolicy.validateContentMatchesExtension(name, content) == null;
    }

    private static boolean isZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && ((bytes[2] == 3 && bytes[3] == 4) || (bytes[2] == 5 && bytes[3] == 6));
    }

    private static boolean isCrx(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'C' && bytes[1] == 'r' && bytes[2] == '2' && bytes[3] == '4';
    }

    private static byte[] unwrapCrx(byte[] bytes) throws IOException {
        if (!isCrx(bytes)) return bytes;
        if (bytes.length < 12) throw new IOException("Truncated CRX header");
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int version = header.getInt(4);
        long offset;
        if (version == 3) offset = 12L + Integer.toUnsignedLong(header.getInt(8));
        else if (version == 2 && bytes.length >= 16) {
            offset = 16L + Integer.toUnsignedLong(header.getInt(8)) + Integer.toUnsignedLong(header.getInt(12));
        } else throw new IOException("Unsupported CRX header version");
        if (offset >= bytes.length) throw new IOException("Invalid CRX header length");
        return java.util.Arrays.copyOfRange(bytes, (int) offset, bytes.length);
    }

    private static byte[] bounded(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("Plugin scan package exceeds size limit");
        return bytes;
    }

    Path path() { return path; }

    String originalPath(String scanPath) { return scanPath == null ? null : originalPaths.getOrDefault(scanPath, scanPath); }

    @Override
    public void close() throws IOException {
        if (!temporary) return;
        if (Files.isDirectory(path)) {
            try (var files = Files.walk(path)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        } else Files.deleteIfExists(path);
    }
}
