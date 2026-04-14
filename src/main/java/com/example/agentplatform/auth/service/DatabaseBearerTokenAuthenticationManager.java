package com.example.agentplatform.auth.service;

import com.example.agentplatform.auth.domain.AppUser;
import com.example.agentplatform.auth.domain.AuthenticatedUserPrincipal;
import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.common.exception.ApplicationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Bearer Token 认证管理器。
 * 使用数据库中的访问令牌换取 Spring Security 认证对象。
 */
@Component
public class DatabaseBearerTokenAuthenticationManager implements ReactiveAuthenticationManager {

    private final AuthService authService;

    public DatabaseBearerTokenAuthenticationManager(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (authentication == null || authentication.getCredentials() == null) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> authService.loadAuthenticatedUser(authentication.getCredentials().toString()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(ApplicationException.class, exception -> new BadCredentialsException(exception.getMessage(), exception))
                .map(this::toAuthentication);
    }

    private Authentication toAuthentication(AppUser user) {
        List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(SecurityRole::authority)
                .map(SimpleGrantedAuthority::new)
                .toList();
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                user.id(),
                user.username(),
                user.passwordHash(),
                authorities
        );
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
