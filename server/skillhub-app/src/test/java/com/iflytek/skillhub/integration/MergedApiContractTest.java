package com.iflytek.skillhub.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Keeps both the custom resources and upstream Suite API contracts during integration. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MergedApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void publishesBothContractsWithoutRemovedUsageEndpoints() throws Exception {
        String document = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var api = json.readTree(document);
        var properties = api.path("components").path("schemas").path("SkillDetailResponse").path("properties");
        assertThat(properties.has("resourceType")).isTrue();
        assertThat(properties.has("entryForSuites")).isTrue();
        assertThat(api.path("components").path("schemas").has("DemandResponse")).isTrue();
        assertThat(api.path("components").path("schemas").path("LabelDefinitionResponse")
                .path("properties").has("category")).isTrue();
        assertThat(document).doesNotContain("usage-stats", "skill-usage-events");
        String output = System.getProperty("skillhub.openapi.output");
        if (output != null) Files.writeString(Path.of(output), document);
    }
}
