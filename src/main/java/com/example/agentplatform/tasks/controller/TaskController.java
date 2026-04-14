package com.example.agentplatform.tasks.controller;

import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.dto.TaskCreateRequest;
import com.example.agentplatform.tasks.dto.TaskStatusUpdateRequest;
import com.example.agentplatform.tasks.service.TaskService;
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

/**
 * 任务接口控制器。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final AuthenticatedUserAccessor authenticatedUserAccessor;
    private final TaskService taskService;

    public TaskController(
            AuthenticatedUserAccessor authenticatedUserAccessor,
            TaskService taskService
    ) {
        this.authenticatedUserAccessor = authenticatedUserAccessor;
        this.taskService = taskService;
    }

    /** 创建一个独立任务或工作流内任务。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TaskRecord> create(
            @Valid @RequestBody TaskCreateRequest request,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> taskService.create(authenticatedUserAccessor.requireUserId(authentication), request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 查询单个任务详情。 */
    @GetMapping("/{taskId}")
    public Mono<TaskRecord> get(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> taskService.get(authenticatedUserAccessor.requireUserId(authentication), taskId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 推进任务状态。 */
    @PostMapping("/{taskId}/status")
    public Mono<TaskRecord> updateStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody TaskStatusUpdateRequest request,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> taskService.updateStatus(
                        authenticatedUserAccessor.requireUserId(authentication),
                        taskId,
                        request
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 请求取消任务。 */
    @PostMapping("/{taskId}/cancel")
    public Mono<TaskRecord> cancel(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> taskService.requestCancel(authenticatedUserAccessor.requireUserId(authentication), taskId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
