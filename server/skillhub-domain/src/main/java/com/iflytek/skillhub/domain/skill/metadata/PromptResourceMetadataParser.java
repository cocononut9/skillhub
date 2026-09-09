package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Plain Markdown prompts use a fixed file name and preserve their original bytes. */
public class PromptResourceMetadataParser {
    public Optional<SkillMetadata> parse(List<PackageEntry> entries) {
        var readme = entries.stream().filter(e -> "README.md".equals(e.path())).findFirst();
        if (readme.isEmpty()) return Optional.empty();
        var metadata = parseReadme(new String(readme.get().content(), StandardCharsets.UTF_8));
        if (metadata.isEmpty()) return metadata;
        var prompt = entries.stream().filter(e -> "PROMPT.md".equals(e.path())).findFirst().orElseThrow(this::invalid);
        if (entries.size() != 2 || prompt.content().length >= 10 * 1024 * 1024) throw invalid();
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(prompt.content())).toString();
            if (text.replace("\uFEFF", "").isBlank() || text.indexOf('\0') >= 0) throw invalid();
        } catch (java.nio.charset.CharacterCodingException e) { throw invalid(); }
        return metadata;
    }

    public Optional<SkillMetadata> parseReadme(String content) {
        Map<String, String> fields = new LinkedHashMap<>();
        boolean duplicate = false;
        for (String line : content.split("\\R")) {
            if (line.startsWith("## ")) break;
            String[] pair = line.trim().split("[：:]", 2);
            if (pair.length == 2 && Set.of("资源类型", "资源标识", "版本").contains(pair[0])) {
                if (fields.putIfAbsent(pair[0], pair[1].trim()) != null) duplicate = true;
            }
        }
        String type = fields.get("资源类型");
        if (!"提示词".equals(type) && !"PROMPT".equalsIgnoreCase(type)) return Optional.empty();
        if (duplicate) throw invalid();
        var presentation = new ReadmePresentationParser().parse(content);
        String name = fields.getOrDefault("资源标识", presentation.displayName());
        SlugValidator.slugify(name);
        return Optional.of(new SkillMetadata(name, presentation.summary(), fields.get("版本"), content,
                Map.of("resourceType", "PROMPT", "promptFile", "PROMPT.md")));
    }

    private DomainBadRequestException invalid() {
        return new DomainBadRequestException("error.resource.prompt.invalid");
    }
}
