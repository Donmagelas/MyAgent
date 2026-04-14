package com.example.agentplatform.workflow.controller;

import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.dto.WorkflowCreateRequest;
import com.example.agentplatform.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 工作流接口控制器。
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final AuthenticatedUserAccessor authenticatedUserAccessor;
    private final WorkflowService workflowService;

    public WorkflowController(
            AuthenticatedUserAccessor authenticatedUserAccessor,
            WorkflowService workflowService
    ) {
        this.authenticatedUserAccessor = authenticatedUserAccessor;
        this.workflowService = workflowService;
    }

    /** 创建一个工作流实例及其任务图。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WorkflowRecord> create(
            @Valid @RequestBody WorkflowCreateRequest request,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> workflowService.create(authenticatedUserAccessor.requireUserId(authentication), request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 查询工作流详情。 */
    @GetMapping("/{workflowId}")
    public Mono<WorkflowRecord> get(
            @PathVariable Long workflowId,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> workflowService.get(authenticatedUserAccessor.requireUserId(authentication), workflowId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 查询工作流下的任务列表。 */
    @GetMapping("/{workflowId}/tasks")
    public Mono<List<TaskRecord>> tasks(
            @PathVariable Long workflowId,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> workflowService.listTasks(authenticatedUserAccessor.requireUserId(authentication), workflowId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
