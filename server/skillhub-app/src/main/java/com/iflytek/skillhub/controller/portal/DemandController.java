package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.demand.DemandService;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.repository.DemandQueryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Authenticated employee demand intake; no development assignment or delivery workflow. */
@RestController
@Validated
@PreAuthorize("isAuthenticated()")
@RequestMapping({"/api/v1/demands", "/api/web/demands"})
public class DemandController extends BaseApiController {
    private final DemandService service;
    private final DemandQueryRepository queries;
    private final ApiResponseFactory responses;

    public DemandController(ApiResponseFactory responses, DemandService service, DemandQueryRepository queries) {
        super(responses);
        this.service = service;
        this.queries = queries;
        this.responses = responses;
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> invalidQuery() {
        return responses.error(400, "error.demand.invalidContent");
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(operationId = "listDemands")
    public ApiResponse<DemandPageResponse> list(@AuthenticationPrincipal PlatformPrincipal principal,
            @RequestParam(defaultValue = "") @Size(max = 120) String q,
            @RequestParam(defaultValue = "") @Size(max = 80) String category,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean includeHidden,
            @RequestParam(defaultValue = "newest") @Pattern(regexp = "newest|popular") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok("response.success.read", queries.list(principal.userId(), q, category, mine,
                includeHidden && admin(principal), sort, page, size));
    }

    @GetMapping("/{id}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "getDemand")
    public ApiResponse<DemandResponse> detail(@PathVariable Long id, @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", queries.detail(id, principal.userId(), admin(principal)));
    }

    @PostMapping
    @io.swagger.v3.oas.annotations.Operation(operationId = "createDemand")
    public ApiResponse<Long> create(@Valid @RequestBody DemandRequest request, @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created", service.create(principal.userId(), content(request)));
    }

    @PutMapping("/{id}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "editDemand")
    public ApiResponse<Void> edit(@PathVariable Long id, @Valid @RequestBody DemandRequest request,
                                 @AuthenticationPrincipal PlatformPrincipal principal) {
        service.edit(id, principal.userId(), content(request));
        return ok("response.success.updated", null);
    }

    @PutMapping("/{id}/support")
    @io.swagger.v3.oas.annotations.Operation(operationId = "supportDemand")
    public ApiResponse<Void> support(@PathVariable Long id, @AuthenticationPrincipal PlatformPrincipal principal) {
        service.support(id, principal.userId(), true);
        return ok("response.success.updated", null);
    }

    @DeleteMapping("/{id}/support")
    @io.swagger.v3.oas.annotations.Operation(operationId = "withdrawDemandSupport")
    public ApiResponse<Void> unsupport(@PathVariable Long id, @AuthenticationPrincipal PlatformPrincipal principal) {
        service.support(id, principal.userId(), false);
        return ok("response.success.updated", null);
    }

    @GetMapping("/{id}/supplements")
    @io.swagger.v3.oas.annotations.Operation(operationId = "listDemandSupplements")
    public ApiResponse<DemandSupplementPageResponse> supplements(@PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok("response.success.read", queries.supplements(id, principal.userId(), admin(principal), page, size));
    }

    @PostMapping("/{id}/supplements")
    @io.swagger.v3.oas.annotations.Operation(operationId = "createDemandSupplement")
    public ApiResponse<Long> supplement(@PathVariable Long id, @Valid @RequestBody DemandSupplementRequest request,
                                       @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created", service.supplement(id, principal.userId(), request.content()));
    }

    @PutMapping("/{id}/supplements/{supplementId}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "editDemandSupplement")
    public ApiResponse<Void> editSupplement(@PathVariable Long id, @PathVariable Long supplementId,
            @Valid @RequestBody DemandSupplementRequest request, @AuthenticationPrincipal PlatformPrincipal principal) {
        service.editSupplement(id, supplementId, principal.userId(), request.content());
        return ok("response.success.updated", null);
    }

    @DeleteMapping("/{id}/supplements/{supplementId}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "deleteDemandSupplement")
    public ApiResponse<Void> deleteSupplement(@PathVariable Long id, @PathVariable Long supplementId,
                                            @AuthenticationPrincipal PlatformPrincipal principal) {
        service.deleteSupplement(id, supplementId, principal.userId());
        return ok("response.success.updated", null);
    }

    @PutMapping("/{id}/visibility")
    @io.swagger.v3.oas.annotations.Operation(operationId = "setDemandVisibility")
    public ApiResponse<Void> visibility(@PathVariable Long id, @RequestParam boolean hidden,
                                       @AuthenticationPrincipal PlatformPrincipal principal) {
        service.moderate(id, null, principal.userId(), admin(principal), hidden);
        return ok("response.success.updated", null);
    }

    @PutMapping("/{id}/supplements/{supplementId}/visibility")
    @io.swagger.v3.oas.annotations.Operation(operationId = "setDemandSupplementVisibility")
    public ApiResponse<Void> supplementVisibility(@PathVariable Long id, @PathVariable Long supplementId,
            @RequestParam boolean hidden, @AuthenticationPrincipal PlatformPrincipal principal) {
        service.moderate(id, supplementId, principal.userId(), admin(principal), hidden);
        return ok("response.success.updated", null);
    }

    private boolean admin(PlatformPrincipal principal) {
        return principal.platformRoles().contains("SUPER_ADMIN") || principal.platformRoles().contains("SKILL_ADMIN");
    }

    private DemandService.Content content(DemandRequest r) {
        return new DemandService.Content(r.title(), r.scenario(), r.expectedResult(), r.category(),
                r.frequency(), r.currentTimeCost(), r.usageScope());
    }
}
