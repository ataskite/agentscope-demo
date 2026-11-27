package com.skloda.agentscope.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * P0-1 security fix: minimal API token authentication filter.
 *
 * <p>Protects all sensitive endpoints ({@code /chat/**}, {@code /api/**},
 * {@code /channel/**}, {@code /a2a/**}, {@code /ag-ui/**}) with a shared token.
 * Covers P2-6 (unauthenticated management endpoints) under the same gate.</p>
 *
 * <p>The token is sent via the configured header (default {@code X-API-Token});
 * {@code /chat/download} style browser-navigation GET links may alternatively pass
 * {@code ?apiToken=} as a query parameter (documented trade-off: token may appear
 * in access logs).</p>
 */
public class ApiTokenAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenAuthFilter.class);

    private static final String[] PROTECTED_PREFIXES = {
            "/chat/", "/api/", "/channel/", "/a2a/", "/ag-ui/"
    };

    private final SecurityProperties properties;
    private final byte[] expectedToken;

    public ApiTokenAuthFilter(SecurityProperties properties) {
        this.properties = properties;
        String configured = properties.getApiToken();
        if (configured == null || configured.isBlank()) {
            configured = generateToken();
            log.warn("agentscope.security.api-token is not configured; generated a random token for this run. "
                    + "Set AGENTSCOPE_SECURITY_API_TOKEN for a stable token. Token: {}", configured);
        }
        this.expectedToken = configured.getBytes(StandardCharsets.UTF_8);
    }

    private static String generateToken() {
        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        if (!properties.isEnabled() || !isProtected(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String provided = httpRequest.getHeader(properties.getHeaderName());
        if (provided == null || provided.isBlank()) {
            // Query parameter fallback for browser-navigation endpoints (e.g. download links)
            provided = httpRequest.getParameter("apiToken");
        }

        if (provided == null || !tokenMatches(provided)) {
            log.warn("Rejected unauthenticated request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"error\":\"Unauthorized: missing or invalid API token\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : PROTECTED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Constant-time comparison to avoid timing side channels. */
    private boolean tokenMatches(String provided) {
        return MessageDigest.isEqual(expectedToken, provided.getBytes(StandardCharsets.UTF_8));
    }
}
