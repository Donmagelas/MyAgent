package com.example.agentplatform.observability.controller;

import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.observability.dto.WorkflowExecutionView;
import com.example.agentplatform.observability.service.WorkflowExecutionVisualizationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 执行可视化查询接口。
 * 为前端提供流程概览、当前步骤与 token 消耗的聚合视图。
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final AuthenticatedUserAccessor authenticatedUserAccessor;
    private final WorkflowExecutionVisualizationService workflowExecutionVisualizationService;

    public ObservabilityController(
            AuthenticatedUserAccessor authenticatedUserAccessor,
            WorkflowExecutionVisualizationService workflowExecutionVisualizationService
    ) {
        this.authenticatedUserAccessor = authenticatedUserAccessor;
        this.workflowExecutionVisualizationService = workflowExecutionVisualizationService;
    }

    /**
     * 查询某个工作流的执行可视化视图。
     */
    @GetMapping("/workflows/{workflowId}")
    public Mono<WorkflowExecutionView> getWorkflowExecution(
            @PathVariable Long workflowId,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> workflowExecutionVisualizationService.getWorkflowExecution(
                        authenticatedUserAccessor.requireUserId(authentication),
                        workflowId
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
