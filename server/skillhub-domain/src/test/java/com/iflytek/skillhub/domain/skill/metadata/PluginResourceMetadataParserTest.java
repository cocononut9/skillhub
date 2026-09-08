package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PluginResourceMetadataParserTest {
    private final PluginResourceMetadataParser parser = new PluginResourceMetadataParser();
    private String readme(String file) {
        return "# 插件测试\n\n> 下载并安装插件。\n\n资源类型：插件\n安装包：" + file + "\n资源标识：plugin-demo\n版本：1.0.0\n";
    }
    private PackageEntry doc(String file) {
        byte[] bytes = readme(file).getBytes(StandardCharsets.UTF_8);
        return new PackageEntry("README.md", bytes, bytes.length, "text/markdown");
    }
    @ParameterizedTest
    @ValueSource(strings = {"demo.zip", "demo.crx", "demo.xpi", "demo.vsix", "demo.exe", "demo.msi", "demo.dmg", "demo.pkg", "demo.tgz", "demo.tar.gz"})
    void acceptsOpaqueInstallerAndPreservesItsName(String file) {
        var entries = List.of(doc(file), new PackageEntry(file, new byte[]{0,1,2}, 3, "application/octet-stream"));
        assertEquals(file, parser.parse(entries).orElseThrow().frontmatter().get("installerFile"));
        var result = new SkillPackageValidator(new SkillMetadataParser()).validate(entries);
        assertTrue(result.passed(), result.errors().toString());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }
    @ParameterizedTest
    @ValueSource(strings = {"../demo.zip", "/demo.zip", "folder/demo.zip", "https://example.com/demo.zip", "SKILL.md", "demo.html", ""})
    void rejectsInvalidInstallerReference(String file) {
        assertThrows(DomainBadRequestException.class, () -> parser.parseReadme(readme(file)));
    }
    @Test
    void requiresActualNonemptyInstallerAndRejectsExtraFiles() {
        assertThrows(DomainBadRequestException.class, () -> parser.parse(List.of(doc("demo.zip"))));
        assertThrows(DomainBadRequestException.class, () -> parser.parse(List.of(doc("demo.zip"), new PackageEntry("demo.zip", new byte[0], 0, "application/zip"))));
        var entries = List.of(doc("demo.zip"), new PackageEntry("demo.zip", new byte[]{1}, 1, "application/zip"), new PackageEntry("other.txt", new byte[]{1}, 1, "text/plain"));
        assertThrows(DomainBadRequestException.class, () -> parser.parse(entries));
    }
    @Test
    void preservesOtherResourceTypesAndRejectsDuplicatePluginFields() {
        assertTrue(parser.parseReadme("# 普通技能\n\n> 说明。").isEmpty());
        assertThrows(DomainBadRequestException.class, () -> parser.parseReadme(readme("demo.zip") + "安装包：other.zip\n"));
        assertTrue(new WebResourceMetadataParser().parse(List.of(doc("demo.zip"))).isEmpty());
    }
}
