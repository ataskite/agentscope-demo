package com.skloda.agentscope.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.DispatcherType;

/**
 * P0-1 security fix: registers the {@link ApiTokenAuthFilter} ahead of every other
 * filter so unauthenticated traffic never reaches chat/API/channel endpoints.
 * {@code /} and static resources ({@code /scripts/**}, {@code /styles/**},
 * {@code /vendor/**}) remain open so the demo UI can load; the frontend attaches
 * the token to its API calls (see {@code static/scripts/api.js}).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<ApiTokenAuthFilter> apiTokenAuthFilter(SecurityProperties properties) {
        FilterRegistrationBean<ApiTokenAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiTokenAuthFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setName("apiTokenAuthFilter");
        return registration;
    }
}
