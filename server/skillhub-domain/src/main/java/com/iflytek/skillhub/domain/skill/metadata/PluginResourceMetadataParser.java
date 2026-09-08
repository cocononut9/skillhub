package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Plugin installers are opaque files: validate the envelope, never unpack the installer. */
public class PluginResourceMetadataParser {
    private static final Set<String> EXTENSIONS = Set.of(".zip", ".crx", ".xpi", ".vsix", ".exe", ".msi", ".dmg", ".pkg", ".tgz", ".tar.gz");

    public Optional<SkillMetadata> parse(List<PackageEntry> entries) {
        var readme = entries.stream().filter(e -> "README.md".equals(e.path())).findFirst();
        if (readme.isEmpty()) return Optional.empty();
        var metadata = parseReadme(new String(readme.get().content(), StandardCharsets.UTF_8));
        if (metadata.isEmpty()) return metadata;
        String installer = (String) metadata.get().frontmatter().get("installerFile");
        if (entries.size() != 2 || entries.stream().noneMatch(e -> installer.equals(e.path()) && e.size() > 0)) {
            throw invalid();
        }
        return metadata;
    }

    public Optional<SkillMetadata> parseReadme(String content) {
        Map<String, String> fields = new LinkedHashMap<>();
        boolean duplicate = false;
        for (String line : content.split("\\R")) {
            if (line.startsWith("## ")) break;
            String[] pair = line.trim().split("[：:]", 2);
            if (pair.length == 2 && Set.of("资源类型", "安装包", "资源标识", "版本").contains(pair[0])) {
                if (fields.putIfAbsent(pair[0], pair[1].trim()) != null) duplicate = true;
            }
        }
        String type = fields.get("资源类型");
        if (!"插件".equals(type) && !"PLUGIN".equalsIgnoreCase(type)) return Optional.empty();
        if (duplicate) throw invalid();
        String installer = fields.get("安装包");
        try {
            if (installer == null || !installer.equals(SkillPackagePolicy.normalizeEntryPath(installer))
                    || installer.contains("/") || !isInstallerFile(installer)) throw invalid();
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        var presentation = new ReadmePresentationParser().parse(content);
        String name = fields.getOrDefault("资源标识", presentation.displayName());
        SlugValidator.slugify(name);
        return Optional.of(new SkillMetadata(name, presentation.summary(), fields.get("版本"), content,
                Map.of("resourceType", "PLUGIN", "installerFile", installer)));
    }

    public static boolean isInstallerFile(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static DomainBadRequestException invalid() {
        return new DomainBadRequestException("error.resource.plugin.invalid");
    }
}
