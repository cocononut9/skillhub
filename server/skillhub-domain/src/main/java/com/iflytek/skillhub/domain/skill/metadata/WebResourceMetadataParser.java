package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads the fixed metadata block of a README-only website package without fetching its URL. */
public class WebResourceMetadataParser {

    public Optional<SkillMetadata> parse(List<PackageEntry> entries) {
        Optional<PackageEntry> readme = entries.stream().filter(e -> "README.md".equals(e.path())).findFirst();
        if (readme.isEmpty()) return Optional.empty();
        String content = new String(readme.get().content(), StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();
        boolean duplicateField = false;
        for (String line : content.split("\\R")) {
            if (line.startsWith("## ")) break;
            String[] pair = line.trim().split("[：:]", 2);
            if (pair.length == 2 && List.of("资源类型", "使用入口", "资源标识", "版本").contains(pair[0])) {
                if (fields.putIfAbsent(pair[0], pair[1].trim()) != null) duplicateField = true;
            }
        }
        String type = fields.get("资源类型");
        if (type == null || "Skill".equalsIgnoreCase(type) || "插件".equals(type) || "PLUGIN".equalsIgnoreCase(type) || "提示词".equals(type) || "PROMPT".equalsIgnoreCase(type)) return Optional.empty();
        if (duplicateField) throw invalid();
        if (!"网页".equals(type) && !"WEB".equalsIgnoreCase(type)) throw invalid();
        if (entries.stream().anyMatch(e -> "SKILL.md".equals(e.path()))) throw invalid();
        var presentation = new ReadmePresentationParser().parse(content);
        String url = fields.get("使用入口");
        validateUrl(url);
        String name = fields.getOrDefault("资源标识", presentation.displayName());
        SlugValidator.slugify(name);
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("resourceType", "WEB");
        frontmatter.put("websiteUrl", url);
        return Optional.of(new SkillMetadata(name, presentation.summary(), fields.get("版本"), content, frontmatter));
    }

    public String rewriteVersion(String content, String version) {
        var heading = java.util.regex.Pattern.compile("(?m)^## ").matcher(content);
        int boundary = heading.find() ? heading.start() : content.length();
        String header = content.substring(0, boundary)
                .replaceAll("(?m)^[\\t ]*版本[：:][^\\r\\n]*", "");
        return header.stripTrailing() + "\n版本：" + version + "\n\n" + content.substring(boundary);
    }

    public static void validateUrl(String url) {
        try {
            if (url == null || url.isBlank() || url.length() > 2048 || url.contains("\\")) throw invalid();
            URI uri = new URI(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() > 65535) throw invalid();
        } catch (java.net.URISyntaxException e) {
            throw invalid();
        }
    }

    private static DomainBadRequestException invalid() {
        return new DomainBadRequestException("error.resource.web.invalid");
    }
}
