package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.ResourceType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillPublishPreflightTest {
    @Mock private NamespaceRepository namespaces;
    @Mock private SkillRepository skills;
    @Mock private SkillVersionRepository versions;
    @Mock private SecurityScanService scanner;
    @Mock private PrePublishValidator precheck;
    @Mock private ObjectStorageService storage;
    @Mock private ApplicationEventPublisher events;
    private SkillPublishService service;

    @BeforeEach
    void setUp() throws Exception {
        var parser = new SkillMetadataParser();
        service = new SkillPublishService(namespaces, mock(NamespaceMemberRepository.class), skills,
                versions, mock(SkillFileRepository.class), storage, new SkillPackageValidator(parser),
                parser, precheck, new ObjectMapper(), mock(ReviewTaskRepository.class), scanner,
                mock(SkillStorageDeletionCompensationService.class), events, Clock.systemUTC());
        var namespace = new Namespace("test-ns", "Test", "owner");
        setId(namespace, 1L);
        when(namespaces.findBySlug("test-ns")).thenReturn(Optional.of(namespace));
        when(precheck.validate(any())).thenReturn(ValidationResult.pass());
    }

    static Stream<Arguments> scannerCases() {
        return Arrays.stream(ResourceType.values()).flatMap(type ->
                Arrays.stream(SkillVisibility.values()).flatMap(visibility ->
                        Stream.of(false, true).map(enabled -> Arguments.of(type, visibility, enabled))));
    }

    @ParameterizedTest(name = "{0} / {1} / scanner enabled={2}")
    @MethodSource("scannerCases")
    void preflightEnforcesScannerRequirements(ResourceType type, SkillVisibility visibility,
                                             boolean enabled) throws Exception {
        lenient().when(scanner.isEnabled()).thenReturn(enabled);
        var result = service.validateOnly("test-ns", entries(type), "owner", visibility, Set.of("SUPER_ADMIN"));
        boolean allowed = enabled || (visibility == SkillVisibility.PRIVATE
                && (type == ResourceType.SKILL || type == ResourceType.WEB));
        assertEquals(allowed, result.valid(), result.toString());
        assertEquals(allowed ? List.of() : List.of("error.security.scanner.required"), result.errors());
        assertEquals(List.of(), result.warnings());
        assertNoWrites();
    }

    static Stream<Arguments> typeCases() {
        return Arrays.stream(ResourceType.values()).flatMap(existing ->
                Arrays.stream(ResourceType.values()).map(incoming -> Arguments.of(existing, incoming)));
    }

    @ParameterizedTest(name = "existing {0} / incoming {1}")
    @MethodSource("typeCases")
    void preflightRejectsTypeChangesButAllowsSameTypeUpdates(ResourceType existingType,
                                                           ResourceType incomingType) throws Exception {
        lenient().when(scanner.isEnabled()).thenReturn(true);
        var existing = new Skill(1L, "test-resource", "owner", SkillVisibility.PRIVATE);
        setId(existing, 2L);
        existing.setResourceType(existingType);
        when(skills.findByNamespaceIdAndSlug(1L, "test-resource")).thenReturn(List.of(existing));
        var result = service.validateOnly("test-ns", entries(incomingType), "owner",
                SkillVisibility.PRIVATE, Set.of("SUPER_ADMIN"));
        assertEquals(existingType == incomingType, result.valid(), result.toString());
        assertEquals(existingType == incomingType ? List.of() : List.of("error.resource.type.immutable"), result.errors());
        assertEquals("test-resource", result.resolvedSlug());
        assertEquals("2.0.0", result.resolvedVersion());
        assertNoWrites();
    }

    private void assertNoWrites() {
        verify(skills, never()).save(any());
        verify(versions, never()).save(any());
        verifyNoInteractions(storage, events);
        verify(scanner, never()).triggerScan(any(), any(), any());
    }

    private static List<PackageEntry> entries(ResourceType type) throws Exception {
        if (type == ResourceType.SKILL) {
            return List.of(text("SKILL.md", "---\nname: test-resource\ndescription: Test resource\nversion: 2.0.0\n---\nTest body."));
        }
        String fields = switch (type) {
            case WEB -> "资源类型：网页\n使用入口：https://example.com\n";
            case PLUGIN -> "资源类型：插件\n安装包：plugin.zip\n";
            case PROMPT -> "资源类型：提示词\n";
            default -> throw new IllegalArgumentException(type.name());
        };
        var readme = text("README.md", "# Test resource\n\n> Test description\n\n" + fields
                + "资源标识：test-resource\n版本：2.0.0\n");
        if (type == ResourceType.WEB) return List.of(readme);
        if (type == ResourceType.PROMPT) return List.of(readme, text("PROMPT.md", "Summarize the supplied notes."));
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write("{\"manifest_version\":3}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] installer = output.toByteArray();
        return List.of(readme, new PackageEntry("plugin.zip", installer, installer.length, "application/zip"));
    }

    private static PackageEntry text(String path, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new PackageEntry(path, bytes, bytes.length, "text/markdown");
    }

    private static void setId(Object entity, long id) throws Exception {
        var field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
