package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.cli.CliSkillUsageEventRequest;
import com.iflytek.skillhub.dto.cli.CliSkillUsageEventResponse;
import com.iflytek.skillhub.service.SkillUsageAppService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cli/v1/skill-usage-events")
public class CliSkillUsageController extends BaseApiController {

    private final SkillUsageAppService skillUsageAppService;

    public CliSkillUsageController(
            SkillUsageAppService skillUsageAppService,
            ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillUsageAppService = skillUsageAppService;
    }

    @PostMapping
    public ApiResponse<CliSkillUsageEventResponse> ingest(
            @Valid @RequestBody CliSkillUsageEventRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillUsageAppService.IngestResult result = skillUsageAppService.ingest(
                request.namespace(),
                request.slug(),
                request.version(),
                request.eventId(),
                request.client(),
                request.evidenceType(),
                request.occurredAt(),
                principal.userId(),
                userNsRoles != null ? userNsRoles : Map.of());
        return ok("response.success.created", new CliSkillUsageEventResponse(result.accepted()));
    }
}
