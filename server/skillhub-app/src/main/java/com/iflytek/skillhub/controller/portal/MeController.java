package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.MySkillAppService;
import com.iflytek.skillhub.service.SkillLabelProjectionService;
import com.iflytek.skillhub.controller.support.IncludeOptions;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal endpoints scoped to the current authenticated user, such as owned and
 * starred skill listings.
 */
@RestController
@RequestMapping({"/api/v1/me", "/api/web/me"})
public class MeController extends BaseApiController {

    private final MySkillAppService mySkillAppService;
    private final SkillLabelProjectionService skillLabelProjectionService;

    public MeController(MySkillAppService mySkillAppService, SkillLabelProjectionService skillLabelProjectionService,
                        ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.mySkillAppService = mySkillAppService;
        this.skillLabelProjectionService = skillLabelProjectionService;
    }

    @GetMapping("/skills")
    public ApiResponse<PageResponse<SkillSummaryResponse>> listMySkills(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String namespace,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }

        return ok(
                "response.success.read",
                mySkillAppService.listMySkills(principal.userId(), page, size, filter, q, namespace, principal.platformRoles())
        );
    }

    @GetMapping("/stars")
    public ApiResponse<PageResponse<SkillSummaryResponse>> listMyStars(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) java.util.List<String> include,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }

        var response = mySkillAppService.listMyStars(principal.userId(), page, size);
        if (IncludeOptions.includesLabels(include)) {
            var labelsBySkillId = skillLabelProjectionService.labelsBySkillIds(
                    response.items().stream().map(SkillSummaryResponse::id).toList());
            response = new PageResponse<>(
                    response.items().stream().map(item -> item.withLabels(
                            labelsBySkillId.getOrDefault(item.id(), java.util.List.of()))).toList(),
                    response.total(), response.page(), response.size());
        }
        return ok("response.success.read", response);
    }

    @GetMapping("/subscriptions")
    public ApiResponse<PageResponse<SkillSummaryResponse>> listMySubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }

        return ok("response.success.read", mySkillAppService.listMySubscriptions(principal.userId(), page, size));
    }
}
