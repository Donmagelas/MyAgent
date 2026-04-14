package com.example.agentplatform.auth.service;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Bearer Token 解析器。
 * 从 Authorization 头中提取 Token 并交给认证管理器继续处理。
 */
@Component
public class BearerTokenServerAuthenticationConverter implements ServerAuthenticationConverter {

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return Mono.empty();
        }

        String token = authorization.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return Mono.empty();
        }
        return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
    }
}
