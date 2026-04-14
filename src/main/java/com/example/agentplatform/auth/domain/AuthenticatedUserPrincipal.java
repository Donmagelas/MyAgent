package com.example.agentplatform.auth.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 已认证用户主体。
 * 在 Spring Security 上下文中显式携带 userId，供记忆和会话链路复用。
 */
public class AuthenticatedUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUserPrincipal(
            Long userId,
            String username,
            String passwordHash,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.authorities = authorities;
    }

    public Long userId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
