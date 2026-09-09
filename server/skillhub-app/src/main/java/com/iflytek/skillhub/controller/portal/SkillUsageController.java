package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillUsageStatsResponse;
import com.iflytek.skillhub.service.SkillUsageAppService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillUsageController extends BaseApiController {

    private final SkillUsageAppService skillUsageAppService;

    public SkillUsageController(
            SkillUsageAppService skillUsageAppService,
            ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillUsageAppService = skillUsageAppService;
    }

    @GetMapping("/{namespace}/{slug}/usage-stats")
    public ApiResponse<SkillUsageStatsResponse> getStats(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestParam(defaultValue = "30") int days,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillUsageAppService.UsageStats stats = skillUsageAppService.getStats(
                namespace, slug, days, userId, userNsRoles != null ? userNsRoles : Map.of());
        return ok("response.success.read", new SkillUsageStatsResponse(
                stats.windowDays(),
                stats.usageCount(),
                stats.uniqueUserCount(),
                stats.repeatUserCount(),
                stats.client(),
                stats.evidenceTypes(),
                stats.coverage()));
    }
}
