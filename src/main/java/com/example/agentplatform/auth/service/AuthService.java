package com.example.agentplatform.auth.service;

import com.example.agentplatform.auth.domain.AppUser;
import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.auth.dto.AuthLoginRequest;
import com.example.agentplatform.auth.dto.AuthLoginResponse;
import com.example.agentplatform.auth.dto.AuthRegisterRequest;
import com.example.agentplatform.auth.dto.AuthRegisterResponse;
import com.example.agentplatform.auth.repository.AppUserRepository;
import com.example.agentplatform.auth.repository.AuthAccessTokenRepository;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AppSecurityProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 认证服务。
 * 聚合注册、登录、令牌换取用户等核心认证能力。
 */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final AuthAccessTokenRepository authAccessTokenRepository;
    private final AccessTokenService accessTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AppSecurityProperties appSecurityProperties;

    public AuthService(
            AppUserRepository appUserRepository,
            AuthAccessTokenRepository authAccessTokenRepository,
            AccessTokenService accessTokenService,
            PasswordEncoder passwordEncoder,
            AppSecurityProperties appSecurityProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.authAccessTokenRepository = authAccessTokenRepository;
        this.accessTokenService = accessTokenService;
        this.passwordEncoder = passwordEncoder;
        this.appSecurityProperties = appSecurityProperties;
    }

    /** 注册普通聊天用户。 */
    @Transactional
    public AuthRegisterResponse register(AuthRegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (appUserRepository.findByUsername(username).isPresent()) {
            throw new ApplicationException("用户名已存在");
        }

        AppUser user = appUserRepository.create(
                username,
                passwordEncoder.encode(request.password()),
                List.of(SecurityRole.CHAT_USER)
        );
        return new AuthRegisterResponse(user.id(), user.username(), user.roles(), user.createdAt());
    }

    /** 校验用户名密码并签发新的 Bearer Token。 */
    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        String username = normalizeUsername(request.username());
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new ApplicationException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new ApplicationException("用户名或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new ApplicationException("当前用户不可登录");
        }

        OffsetDateTime now = OffsetDateTime.now();
        authAccessTokenRepository.revokeActiveTokensByUserId(user.id(), now);

        String plainToken = accessTokenService.generatePlainToken();
        String tokenHash = accessTokenService.hashToken(plainToken);
        OffsetDateTime expiresAt = now.plusHours(appSecurityProperties.tokenTtlHours());
        authAccessTokenRepository.save(user.id(), tokenHash, expiresAt);

        return new AuthLoginResponse(
                user.id(),
                user.username(),
                "Bearer",
                plainToken,
                expiresAt,
                user.roles()
        );
    }

    /** 供安全过滤器根据 Token 解析已登录用户。 */
    public AppUser loadAuthenticatedUser(String plainToken) {
        String tokenHash = accessTokenService.hashToken(plainToken);
        Long userId = authAccessTokenRepository.findActiveUserIdByTokenHash(tokenHash, OffsetDateTime.now())
                .orElseThrow(() -> new ApplicationException("登录态无效或已过期"));
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException("令牌关联用户不存在"));
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new ApplicationException("当前用户不可用");
        }
        return user;
    }

    /** 供启动引导阶段同步本地默认账号。 */
    @Transactional
    public void upsertBootstrapUser(String username, String password, List<String> roles) {
        appUserRepository.upsertBootstrapUser(
                normalizeUsername(username),
                passwordEncoder.encode(password),
                roles
        );
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isEmpty()) {
            throw new ApplicationException("用户名不能为空");
        }
        return normalized;
    }
}
