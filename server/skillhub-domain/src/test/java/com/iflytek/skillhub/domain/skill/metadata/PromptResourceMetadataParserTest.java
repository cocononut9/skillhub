package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PromptResourceMetadataParserTest {
    private final PromptResourceMetadataParser parser = new PromptResourceMetadataParser();
    private PackageEntry entry(String path, byte[] bytes) { return new PackageEntry(path, bytes, bytes.length, "text/markdown"); }
    private PackageEntry readme() { return entry("README.md", "# 文案提示词\n\n> 生成产品文案。\n\n资源类型：提示词\n资源标识：copywriting-prompt\n版本：1.0.0\n".getBytes(StandardCharsets.UTF_8)); }
    @Test
    void acceptsPlainMarkdownWithoutSkillManifest() {
        var entries = List.of(readme(), entry("PROMPT.md", "你是文案助手。\n\n```text\n{{产品}}\n```\n".getBytes(StandardCharsets.UTF_8)));
        var parsed = parser.parse(entries).orElseThrow();
        assertEquals("PROMPT", parsed.frontmatter().get("resourceType"));
        assertEquals("copywriting-prompt", parsed.name());
        assertTrue(new SkillPackageValidator(new SkillMetadataParser()).validate(entries).passed());
    }
    @Test
    void rejectsMissingBlankBinaryAndAdditionalFiles() {
        assertThrows(DomainBadRequestException.class, () -> parser.parse(List.of(readme())));
        for (byte[] invalid : List.of(new byte[0], " \n\t".getBytes(), new byte[]{(byte)0xff}, new byte[]{0, 65})) {
            assertThrows(DomainBadRequestException.class, () -> parser.parse(List.of(readme(), entry("PROMPT.md", invalid))));
        }
        assertThrows(DomainBadRequestException.class, () -> parser.parse(List.of(readme(), entry("PROMPT.md", new byte[]{65}), entry("SKILL.md", new byte[]{65}))));
    }
    @Test
    void rejectsDuplicateTypeAndLeavesOtherTypesAlone() {
        assertThrows(DomainBadRequestException.class, () -> parser.parseReadme(new String(readme().content(), StandardCharsets.UTF_8)+"资源类型：提示词\n"));
        assertTrue(parser.parseReadme("# 普通文档\n\n> 介绍。\n").isEmpty());
        assertTrue(new WebResourceMetadataParser().parse(List.of(readme())).isEmpty());
    }
}
