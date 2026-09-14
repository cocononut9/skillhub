package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class WebResourceMetadataParserTest {
    private final WebResourceMetadataParser parser = new WebResourceMetadataParser();
    private List<PackageEntry> entries(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return List.of(new PackageEntry("README.md", bytes, bytes.length, "text/markdown"));
    }
    private String readme(String url) {
        return "# 营销助手\n\n> 生成营销文案。\n\n资源类型：网页\n使用入口：" + url + "\n资源标识：marketing-helper\n版本：1.0.0\n\n## 使用方法\n打开网页。";
    }
    @Test
    void parsesWebsiteWithoutSkillMdAndPassesPackageValidation() {
        var entries = entries(readme("https://example.com/tool?q=1"));
        var metadata = parser.parse(entries).orElseThrow();
        assertEquals("marketing-helper", metadata.name());
        assertEquals("1.0.0", metadata.version());
        assertEquals("https://example.com/tool?q=1", metadata.frontmatter().get("websiteUrl"));
        assertTrue(new SkillPackageValidator(new SkillMetadataParser()).validate(entries).passed());
    }
    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "//example.com", "https://user:pass@example.com", "", "https://", "file:///tmp/index.html"})
    void rejectsInvalidWebsiteUrl(String url) {
        assertThrows(DomainBadRequestException.class, () -> parser.parse(entries(readme(url))));
    }
    @Test
    void rejectsAmbiguousSkillAndWebsitePackage() {
        var files = new java.util.ArrayList<>(entries(readme("https://example.com")));
        files.add(new PackageEntry("SKILL.md", new byte[0], 0, "text/markdown"));
        assertThrows(DomainBadRequestException.class, () -> parser.parse(files));
    }
    @Test
    void keepsOrdinaryReadmeAsSkillAndRejectsDuplicateFields() {
        assertTrue(parser.parse(entries("# 普通技能\n\n> 说明。")).isEmpty());
        assertThrows(DomainBadRequestException.class, () -> parser.parse(entries(readme("https://example.com").replace("\n## 使用方法", "\n使用入口：https://another.example\n## 使用方法"))));
    }
    @Test
    void rereleaseRewritesOnlyHeaderAndPreservesBodyVersionText() {
        String original = readme("https://example.com").replace("版本：1.0.0", "  版本：1.0.0")
                + "\n版本：正文中的示例";
        String rewritten = parser.rewriteVersion(original, "2.0.0");
        assertEquals("2.0.0", parser.parse(entries(rewritten)).orElseThrow().version());
        assertTrue(rewritten.endsWith("版本：正文中的示例"));
    }
    @Test
    void ignoresRepeatedOrdinaryReadmeFieldsWithoutWebsiteType() {
        assertTrue(parser.parse(entries("# 普通技能\n\n> 说明。\n版本：1\n版本：2")).isEmpty());
    }
    @Test
    void websiteStillEnforcesPackageLimits() {
        var validator = new SkillPackageValidator(new SkillMetadataParser(), 0, 1, 1, java.util.Set.of(".md"));
        assertFalse(validator.validate(entries(readme("https://example.com"))).passed());
    }
}
