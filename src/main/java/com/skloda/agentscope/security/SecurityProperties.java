package com.skloda.agentscope.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P0-1 security fix: API token authentication properties.
 *
 * <p>All sensitive HTTP endpoints ({@code /chat/**}, {@code /api/**},
 * {@code /channel/**}, {@code /a2a/**}, {@code /ag-ui/**}) require the configured
 * token by default. The root page ({@code /}) and static resources stay open so the
 * demo UI can load; API calls must carry the token.</p>
 *
 * <p>Configuration (env vars supported via Spring relaxed binding):</p>
 * <pre>
 * agentscope.security.enabled=true      # AGENTSCOPE_SECURITY_ENABLED
 * agentscope.security.api-token=...     # AGENTSCOPE_SECURITY_API_TOKEN
 * agentscope.security.header-name=X-API-Token
 * </pre>
 *
 * <p>When {@code api-token} is blank, a secure random token is generated at startup
 * and logged once (local development convenience). Fail-closed: an empty configured
 * token never disables authentication — only {@code enabled=false} does.</p>
 */
@ConfigurationProperties(prefix = "agentscope.security")
public class SecurityProperties {

    /** Master switch. Default true (secure by default). */
    private boolean enabled = true;

    /** Shared API token. Blank -> generated at startup and logged. */
    private String apiToken = "";

    /** HTTP header (and query parameter fallback for download links) carrying the token. */
    private String headerName = "X-API-Token";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
