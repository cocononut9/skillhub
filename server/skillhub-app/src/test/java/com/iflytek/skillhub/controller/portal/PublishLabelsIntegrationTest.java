package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.label.*;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.service.LabelSearchSyncService;
import com.iflytek.skillhub.service.SkillLabelAppService;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"skillhub.builtin-skills.enabled=false", "skillhub.bootstrap.admin.enabled=false",
        "skillhub.label.max-per-skill=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class PublishLabelsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserAccountRepository users;
    @Autowired NamespaceRepository namespaces;
    @Autowired NamespaceMemberRepository members;
    @Autowired LabelDefinitionService definitions;
    @Autowired SkillLabelService labels;
    @Autowired SkillLabelAppService labelApp;
    @Autowired SkillRepository skills;
    @Autowired SkillVersionRepository versions;
    @MockBean RbacService rbac;
    @MockBean SecurityScanService scanner;
    @MockBean ObjectStorageService storage;
    @MockBean SearchRebuildService search;
    @MockBean LabelSearchSyncService labelSearch;

    private String owner;
    private String admin;
    private Namespace namespace;
    private String ordinary;
    private String second;
    private String third;
    private String privileged;

    @Test
    void uploadApiContractIncludesOptionalExistingLabels() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var operation = mapper.readTree(body).path("paths").path("/api/web/skills/{namespace}/publish").path("post");
        assertThat(operation.toString()).contains("labelSlugs");
        assertThat(mapper.readTree(body).path("components").path("schemas").path("SkillLabelDto")
                .path("properties").path("category").toString()).contains("WORKFLOW", "ROLE", "GENERAL");
        String output = System.getProperty("skillhub.openapi.output");
        if (output != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(output), body, StandardCharsets.UTF_8);
        }
    }

    @Test
    void categoriesRoundTripAndLegacyUpdatesPreserveGrouping() throws Exception {
        String slug = "workflow-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/labels").with(asUser(admin, true)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"slug":"%s","type":"RECOMMENDED","category":"WORKFLOW",
                                 "visibleInFilter":true,"sortOrder":0,
                                 "translations":[{"locale":"zh","displayName":"品牌营销"}]}
                                """.formatted(slug)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("WORKFLOW"));
        long id = upload("1.0.0", slug, ordinary);
        assertThat(labelApp.listSkillLabelsBySkillId(id)).filteredOn("slug", slug)
                .extracting("category").containsExactly(LabelCategory.WORKFLOW);
        mvc.perform(put("/api/v1/admin/labels/" + slug).with(asUser(admin, true)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"RECOMMENDED","visibleInFilter":true,"sortOrder":0,
                                 "translations":[{"locale":"zh","displayName":"市场洞察"}]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("WORKFLOW"));
        mvc.perform(put("/api/v1/admin/labels/" + slug).with(asUser(admin, true)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"RECOMMENDED","category":"ROLE","visibleInFilter":true,"sortOrder":0,
                                 "translations":[{"locale":"zh","displayName":"品牌专员"}]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("ROLE"));
        assertThat(labels.listSkillLabels(id)).hasSize(2);
        mvc.perform(get("/api/web/labels").header("Accept-Language", "zh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.slug == '%s')].category".formatted(slug)).value("ROLE"));
    }

    @Test
    void invalidCategoryIsRejectedWithoutCreatingLabel() throws Exception {
        mvc.perform(post("/api/v1/admin/labels").with(asUser(admin, true)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"slug":"invalid-category","type":"RECOMMENDED","category":"DEPARTMENT",
                                 "visibleInFilter":true,"sortOrder":0,
                                 "translations":[{"locale":"zh","displayName":"测试"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = "owner-" + suffix;
        admin = "admin-" + suffix;
        users.save(new UserAccount(owner, "上传者", null, null));
        users.save(new UserAccount(admin, "管理员", null, null));
        namespace = namespaces.save(new Namespace("labels-" + suffix, "标签测试", owner));
        members.save(new NamespaceMember(namespace.getId(), owner, NamespaceRole.MEMBER));
        when(rbac.getUserRoleCodes(owner)).thenReturn(Set.of());
        when(rbac.getUserRoleCodes(admin)).thenReturn(Set.of("SUPER_ADMIN"));
        when(scanner.isEnabled()).thenReturn(true);
        ordinary = createLabel("ordinary-" + suffix, LabelType.RECOMMENDED);
        second = createLabel("second-" + suffix, LabelType.RECOMMENDED);
        third = createLabel("third-" + suffix, LabelType.RECOMMENDED);
        privileged = createLabel("official-" + suffix, LabelType.PRIVILEGED);
    }

    @Test
    void uploadSavesLabelsAndAdminCanRenameRemoveAndReplaceThem() throws Exception {
        long id = upload("1.0.0", ordinary, ordinary.toUpperCase());
        assertThat(labels.listSkillLabels(id)).hasSize(1);
        mvc.perform(put("/api/v1/admin/labels/" + ordinary)
                        .with(asUser(admin, true)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"RECOMMENDED","visibleInFilter":true,"sortOrder":0,
                                 "translations":[{"locale":"en","displayName":"Updated label"}]}
                                """))
                .andExpect(status().isOk());
        assertThat(labelApp.listSkillLabelsBySkillId(id)).extracting("displayName").containsExactly("Updated label");
        mvc.perform(delete(resourceLabels() + ordinary).with(asUser(admin, true)).with(csrf()))
                .andExpect(status().isOk());
        assertThat(labels.listSkillLabels(id)).isEmpty();
        assertThat(definitions.getBySlug(ordinary)).isNotNull();
        mvc.perform(put(resourceLabels() + privileged).with(asUser(admin, true)).with(csrf()))
                .andExpect(status().isOk());
        assertThat(labelApp.listSkillLabelsBySkillId(id)).extracting("slug").containsExactly(privileged);
    }

    @Test
    void updatesPreserveLabelsAndIdempotentSelectionAtCapacity() throws Exception {
        long id = upload("1.0.0", ordinary, second);
        assertThat(upload("2.0.0", ordinary, second)).isEqualTo(id);
        assertThat(upload("3.0.0")).isEqualTo(id);
        assertThat(labels.listSkillLabels(id)).hasSize(2);
        mvc.perform(multipart(uploadUrl()).file(zip("4.0.0")).param("visibility", "PRIVATE")
                        .param("labelSlugs", third).with(asUser(owner, false)).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(versions.findBySkillIdAndVersion(id, "4.0.0")).isEmpty();
        assertThat(labels.listSkillLabels(id)).hasSize(2);
    }

    @Test
    void ordinaryUserCannotInventLabelsSelectPrivilegedLabelsOrManageLibrary() throws Exception {
        mvc.perform(multipart(uploadUrl()).file(zip("1.0.0")).param("visibility", "PRIVATE")
                        .param("labelSlugs", privileged).with(asUser(owner, false)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(uploadUrl()).file(zip("1.0.0")).param("visibility", "PRIVATE")
                        .param("labelSlugs", "missing-label").with(asUser(owner, false)).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(skills.findByNamespaceIdAndSlug(namespace.getId(), "upload-label-demo")).isEmpty();
        mvc.perform(delete("/api/v1/admin/labels/" + ordinary).with(asUser(owner, false)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/labels").with(asUser(owner, false)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"slug":"invented","type":"RECOMMENDED","visibleInFilter":true,"sortOrder":0,
                                 "translations":[{"locale":"en","displayName":"Invented"}]}
                                """))
                .andExpect(status().isForbidden());
    }

    private String createLabel(String slug, LabelType type) {
        return definitions.create(slug, type, true, 0, List.of(new LabelTranslation(null, "en", slug)),
                admin, Set.of("SUPER_ADMIN")).getSlug();
    }

    private long upload(String version, String... slugs) throws Exception {
        var request = multipart(uploadUrl()).file(zip(version)).param("visibility", "PRIVATE")
                .with(asUser(owner, false)).with(csrf());
        if (slugs.length > 0) request.param("labelSlugs", slugs);
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data").path("skillId").asLong();
    }

    private String uploadUrl() { return "/api/web/skills/" + namespace.getSlug() + "/publish"; }
    private String resourceLabels() { return "/api/web/skills/" + namespace.getSlug() + "/upload-label-demo/labels/"; }

    private RequestPostProcessor asUser(String id, boolean isAdmin) {
        Set<String> roles = isAdmin ? Set.of("SUPER_ADMIN") : Set.of();
        var principal = new PlatformPrincipal(id, id, null, "", "local", roles);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
    }

    private MockMultipartFile zip(String version) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(("---\nname: upload-label-demo\ndescription: Label upload test\nversion: " + version + "\n---\nTest skill.")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new MockMultipartFile("file", "labels.zip", "application/zip", bytes.toByteArray());
    }
}
