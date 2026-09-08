package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.metadata.ComplianceMetadataService;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.WebResourceMetadataParser;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates uploaded skill packages against structural, metadata, and size constraints before
 * publish-time domain processing continues.
 */
public class SkillPackageValidator {
    private static final Pattern YAML_LINE_COLUMN = Pattern.compile("line\\s+(\\d+),\\s+column\\s+(\\d+)");

    private final SkillMetadataParser metadataParser;
    private final ComplianceMetadataService complianceMetadataService;
    private final int maxFileCount;
    private final long maxSingleFileSize;
    private final long maxTotalPackageSize;
    private final Set<String> allowedExtensions;

    public SkillPackageValidator(SkillMetadataParser metadataParser) {
        this(
                metadataParser,
                new ComplianceMetadataService(),
                SkillPackagePolicy.MAX_FILE_COUNT,
                SkillPackagePolicy.MAX_SINGLE_FILE_SIZE,
                SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE,
                SkillPackagePolicy.ALLOWED_EXTENSIONS
        );
    }

    public SkillPackageValidator(SkillMetadataParser metadataParser,
                                 int maxFileCount,
                                 long maxSingleFileSize,
                                 long maxTotalPackageSize,
                                 Set<String> allowedExtensions) {
        this(
                metadataParser,
                new ComplianceMetadataService(),
                maxFileCount,
                maxSingleFileSize,
                maxTotalPackageSize,
                allowedExtensions
        );
    }

    public SkillPackageValidator(SkillMetadataParser metadataParser,
                                 ComplianceMetadataService complianceMetadataService,
                                 int maxFileCount,
                                 long maxSingleFileSize,
                                 long maxTotalPackageSize,
                                 Set<String> allowedExtensions) {
        this.metadataParser = metadataParser;
        this.complianceMetadataService = complianceMetadataService;
        this.maxFileCount = maxFileCount;
        this.maxSingleFileSize = maxSingleFileSize;
        this.maxTotalPackageSize = maxTotalPackageSize;
        this.allowedExtensions = allowedExtensions.stream()
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public ValidationResult validate(List<PackageEntry> entries) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> normalizedPaths = new HashSet<>();
        PackageEntry skillMd = null;
        String installerFile = null;
        try {
            var plugin = new com.iflytek.skillhub.domain.skill.metadata.PluginResourceMetadataParser().parse(entries);
            if (plugin.isPresent()) installerFile = (String) plugin.get().frontmatter().get("installerFile");
        } catch (LocalizedDomainException e) {
            errors.add(formatMetadataError(e));
            return ValidationResult.of(errors, warnings);
        }

        for (PackageEntry entry : entries) {
            String normalizedPath;
            try {
                normalizedPath = SkillPackagePolicy.normalizeEntryPath(entry.path());
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
                continue;
            }

            if (!normalizedPaths.add(normalizedPath)) {
                errors.add("Duplicate package entry path: " + normalizedPath);
            }

            if (!normalizedPath.equals(installerFile) && !hasAllowedExtension(normalizedPath)) {
                warnings.add("Disallowed file extension: " + normalizedPath);
            }

            String contentMismatch = normalizedPath.equals(installerFile) ? null
                    : SkillPackagePolicy.validateContentMatchesExtension(normalizedPath, entry.content());
            if (contentMismatch != null) {
                warnings.add(contentMismatch);
            }

            if (SkillPackagePolicy.SKILL_MD_PATH.equals(normalizedPath) && skillMd == null) {
                skillMd = entry;
            }
        }

        boolean webResource = installerFile != null;
        try {
            webResource = webResource || new WebResourceMetadataParser()
                    .parse(entries).isPresent();
        } catch (LocalizedDomainException e) {
            errors.add(formatMetadataError(e));
        }

        // Skill packages require SKILL.md; website packages use README.md.
        // 1. Check SKILL.md exists at root
        if (skillMd == null && !webResource) {
            errors.add("Missing required file: SKILL.md at root");
            return ValidationResult.of(errors, warnings);
        }

        // 2. Validate frontmatter
        try {
            if (!webResource) {
                String content = new String(skillMd.content());
                SkillMetadata metadata = metadataParser.parse(content);
                errors.addAll(complianceMetadataService.validate(metadata.frontmatter(), entries));
            }
        } catch (LocalizedDomainException e) {
            errors.add("Invalid SKILL.md frontmatter: " + formatMetadataError(e));
        }

        // 3. Check file count
        if (entries.size() > maxFileCount) {
            errors.add("Too many files: " + entries.size() + " (max: " + maxFileCount + ")");
        }

        // 4. Check single file size
        for (PackageEntry entry : entries) {
            long fileLimit = entry.path().equals(installerFile) ? maxTotalPackageSize : maxSingleFileSize;
            if (entry.size() > fileLimit) {
                errors.add("File too large: " + entry.path() + " (" + entry.size() + " bytes, max: " + fileLimit + ")");
            }
        }

        // 5. Check total package size
        long totalSize = entries.stream().mapToLong(PackageEntry::size).sum();
        if (totalSize > maxTotalPackageSize) {
            errors.add("Package too large: " + totalSize + " bytes (max: " + maxTotalPackageSize + ")");
        }

        return ValidationResult.of(errors, warnings);
    }

    private boolean hasAllowedExtension(String normalizedPath) {
        String lowercasePath = normalizedPath.toLowerCase(Locale.ROOT);
        return allowedExtensions.stream().anyMatch(lowercasePath::endsWith);
    }

    private String formatMetadataError(LocalizedDomainException exception) {
        return switch (exception.messageCode()) {
            case "error.resource.plugin.invalid" ->
                    "插件 ZIP 必须只包含根目录 README.md 和非空安装包；README 在第一个二级标题前填写资源类型：插件、安装包：实际文件名（支持 zip/crx/xpi/vsix；暂不支持无法静态扫描的二进制安装器），字段不能重复";
            case "error.resource.web.invalid" ->
                    "Invalid website README: use 资源类型：网页 and 使用入口：https://example.com, without duplicate fields, URL credentials or SKILL.md";
            case "error.skill.metadata.requiredField.missing" ->
                    "missing required field \"" + exception.messageArgs()[0] + "\"";
            case "error.skill.metadata.frontmatter.missingStart" ->
                    "missing opening --- marker";
            case "error.skill.metadata.frontmatter.missingEnd" ->
                    "missing closing --- marker";
            case "error.skill.metadata.frontmatter.missingContent" ->
                    "frontmatter is empty";
            case "error.skill.metadata.yaml.notMap" ->
                    "frontmatter must be a YAML object";
            case "error.skill.metadata.yaml.invalid" ->
                    formatYamlSyntaxError(exception.messageArgs());
            default -> {
                if (exception.messageArgs().length == 0) {
                    yield exception.messageCode();
                }
                yield exception.messageCode() + " " + java.util.Arrays.toString(exception.messageArgs());
            }
        };
    }

    private String formatYamlSyntaxError(Object[] args) {
        String raw = args.length > 0 && args[0] != null ? args[0].toString() : "";
        Matcher matcher = YAML_LINE_COLUMN.matcher(raw);
        if (matcher.find()) {
            return "invalid YAML near line " + matcher.group(1)
                    + ", column " + matcher.group(2)
                    + ". If a value contains a colon, wrap it in quotes.";
        }
        return "invalid YAML syntax";
    }
}
