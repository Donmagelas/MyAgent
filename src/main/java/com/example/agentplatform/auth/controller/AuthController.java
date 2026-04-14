package com.example.agentplatform.auth.controller;

import com.example.agentplatform.auth.dto.AuthLoginRequest;
import com.example.agentplatform.auth.dto.AuthLoginResponse;
import com.example.agentplatform.auth.dto.AuthRegisterRequest;
import com.example.agentplatform.auth.dto.AuthRegisterResponse;
import com.example.agentplatform.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 认证接口控制器。
 * 提供注册和登录能力，供前端获取 Bearer Token。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 注册普通聊天用户。 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthRegisterResponse> register(@Valid @RequestBody AuthRegisterRequest request) {
        return Mono.fromCallable(() -> authService.register(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 用户名密码登录并签发 Bearer Token。 */
    @PostMapping("/login")
    public Mono<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return Mono.fromCallable(() -> authService.login(request))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
