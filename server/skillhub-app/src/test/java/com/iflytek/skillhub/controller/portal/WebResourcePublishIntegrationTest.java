package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.*;
import com.iflytek.skillhub.domain.user.*;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = SkillhubApplication.class, properties = {
    "skillhub.builtin-skills.enabled=false", "skillhub.bootstrap.admin.enabled=false"})
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class WebResourcePublishIntegrationTest {
    @Autowired SkillPublishService publish;
    @Autowired SkillPackageArchiveExtractor extractor;
    @Autowired SkillQueryService query;
    @Autowired SkillDownloadService download;
    @Autowired SkillRepository skills;
    @Autowired NamespaceRepository namespaces;
    @Autowired NamespaceMemberRepository members;
    @Autowired UserAccountRepository users;
    @Autowired ReviewTaskRepository reviews;
    @Autowired ReviewService review;
    @MockBean SecurityScanService scanner;
    @MockBean ObjectStorageService storage;
    @MockBean SearchRebuildService search;

    @Test
    void uploadsReadmeOnlyZipApprovesAndProtectsPublishedUrlWhileUpdateIsPending() throws Exception {
        String owner = "web-owner-" + UUID.randomUUID();
        String reviewer = "web-reviewer-" + UUID.randomUUID();
        users.save(new UserAccount(owner, "网页作者", null, null));
        users.save(new UserAccount(reviewer, "网页审核员", null, null));
        Namespace ns = namespaces.save(new Namespace("web-" + UUID.randomUUID(), "网页测试", owner));
        members.save(new NamespaceMember(ns.getId(), owner, NamespaceRole.MEMBER));
        members.save(new NamespaceMember(ns.getId(), reviewer, NamespaceRole.ADMIN));
        when(scanner.isEnabled()).thenReturn(true);
        var entries = extractor.extractWithWarnings(zip("1.0.0", "https://example.com/approved")).entries();
        assertTrue(publish.validateOnly(ns.getSlug(), entries, owner, SkillVisibility.PUBLIC, Set.of()).valid());
        var result = publish.publishFromEntries(ns.getSlug(), entries, owner, SkillVisibility.PUBLIC, Set.of(), true);
        Skill skill = skills.findById(result.skillId()).orElseThrow();
        assertEquals(ResourceType.WEB, skill.getResourceType());
        assertEquals("网页测试工具", skill.getDisplayName());
        assertEquals(SkillVersionStatus.PENDING_REVIEW, result.version().getStatus());
        ReviewTask task = reviews.findBySkillVersionIdAndStatus(result.version().getId(), ReviewTaskStatus.PENDING).orElseThrow();
        review.approveReview(task.getId(), reviewer, "通过", Map.of(ns.getId(), NamespaceRole.ADMIN), Set.of());
        var publicDetail = query.getSkillDetail(ns.getSlug(), "website-demo", null, Map.of());
        assertEquals("WEB", publicDetail.resourceType());
        assertEquals("1.0.0", publicDetail.headlineVersion().version());
        assertTrue(query.getVersionDetail(ns.getSlug(), "website-demo", "1.0.0", null, Map.of()).parsedMetadataJson().contains("https://example.com/approved"));
        assertThrows(DomainBadRequestException.class, () -> query.resolveVersion(ns.getSlug(), "website-demo", null, null, null, null, Map.of()));
        assertThrows(DomainBadRequestException.class, () -> download.downloadLatest(ns.getSlug(), "website-demo", null, Map.of()));
        var next = publish.publishFromEntries(ns.getSlug(), extractor.extractWithWarnings(zip("2.0.0", "https://example.com/pending")).entries(), owner, SkillVisibility.PUBLIC, Set.of(), true);
        assertEquals(SkillVersionStatus.PENDING_REVIEW, next.version().getStatus());
        assertEquals("1.0.0", query.getSkillDetail(ns.getSlug(), "website-demo", null, Map.of()).headlineVersion().version());
    }

    private MockMultipartFile zip(String version, String url) throws Exception {
        String readme = "# 网页测试工具\n\n> 用于测试网页发布。\n\n资源类型：网页\n使用入口：" + url + "\n资源标识：website-demo\n版本：" + version + "\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("website-demo/README.md"));
            zip.write(readme.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new MockMultipartFile("file", "website.zip", "application/zip", bytes.toByteArray());
    }
}
