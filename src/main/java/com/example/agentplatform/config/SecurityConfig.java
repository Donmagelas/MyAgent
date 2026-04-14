package com.example.agentplatform.config;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.auth.service.BearerTokenServerAuthenticationConverter;
import com.example.agentplatform.auth.service.DatabaseBearerTokenAuthenticationManager;
import com.example.agentplatform.common.web.ApiErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * 响应式 Spring Security 配置。
 * 使用数据库中的 Bearer Token 保护聊天、检索和文档接口。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * 配置当前接口的端点级授权规则。
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            AuthenticationWebFilter authenticationWebFilter,
            ObjectMapper objectMapper
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(spec -> spec
                        .authenticationEntryPoint((exchange, exception) -> writeErrorResponse(
                                exchange.getResponse(),
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "请先登录后再访问该接口",
                                objectMapper
                        ))
                        .accessDeniedHandler((exchange, denied) -> writeErrorResponse(
                                exchange.getResponse(),
                                HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                denied.getMessage(),
                                objectMapper
                        )))
                .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/api/memory/**").hasRole(SecurityRole.CHAT_USER)
                        .pathMatchers("/api/skills/**").hasRole(SecurityRole.CHAT_USER)
                        .pathMatchers("/api/tools/**").hasRole(SecurityRole.CHAT_USER)
                        .pathMatchers("/api/tasks/**").hasRole(SecurityRole.CHAT_USER)
                        .pathMatchers("/api/workflows/**").hasRole(SecurityRole.CHAT_USER)
                        .pathMatchers("/api/observability/**").hasRole(SecurityRole.CHAT_USER)
                        .pathMatchers("/api/documents/**").hasRole(SecurityRole.KNOWLEDGE_ADMIN)
                        .pathMatchers("/api/rag/**").hasAnyRole(SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN)
                        .pathMatchers("/api/chat/**").hasRole(SecurityRole.CHAT_USER)
                        .anyExchange().permitAll())
                .build();
    }

    /**
     * 装配 Bearer Token 认证过滤器。
     */
    @Bean
    public AuthenticationWebFilter authenticationWebFilter(
            DatabaseBearerTokenAuthenticationManager authenticationManager,
            BearerTokenServerAuthenticationConverter authenticationConverter
    ) {
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(authenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(authenticationConverter);
        authenticationWebFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        return authenticationWebFilter;
    }

    /**
     * 对配置里的明文密码使用 Spring Security 委托编码器。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private Mono<Void> writeErrorResponse(
            ServerHttpResponse response,
            HttpStatus httpStatus,
            String code,
            String message,
            ObjectMapper objectMapper
    ) {
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = toJsonBytes(new ApiErrorResponse(code, message, OffsetDateTime.now()), objectMapper);
        DataBuffer dataBuffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(dataBuffer));
    }

    private byte[] toJsonBytes(ApiErrorResponse response, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsBytes(response);
        }
        catch (JsonProcessingException exception) {
            String fallback = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"安全响应序列化失败\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
