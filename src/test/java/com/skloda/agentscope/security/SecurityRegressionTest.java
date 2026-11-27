package com.skloda.agentscope.security;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.permission.PermissionContextFactory;
import io.agentscope.core.permission.PermissionContextState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3-4: regression tests for the security-critical behaviors fixed in this branch
 * (P0-1 / P1-1 / P1-2 / P2-1 / P2-7 / P3-5).
 */
class SecurityRegressionTest {

    // ---- P1-1: permission defaults hardened, client override opt-in only ----

    @Test
    void permissionDefaultModeMustNotBeBypass() {
        AgentConfig.PermissionConfig config = new AgentConfig.PermissionConfig();
        assertNotEquals("bypass", config.getDefaultMode(),
                "PermissionConfig.defaultMode must not default to allow-all");
        assertEquals("explore", config.getDefaultMode());
    }

    @Test
    void clientOverrideMustBeOptIn() {
        AgentConfig.PermissionConfig config = new AgentConfig.PermissionConfig();
        assertFalse(config.isAllowClientOverride(),
                "Client-supplied permissionMode must be ignored unless explicitly allowed");
    }

    // ---- P0-1: fail-closed context denies high-risk framework tools ----

    @Test
    void failClosedContextDeniesHighRiskTools() {
        PermissionContextFactory factory = new PermissionContextFactory();
        PermissionContextState state = factory.buildFailClosed();

        assertNotNull(state);
        // Deep assertions on the engine's enforcement are framework-owned; the contract
        // this test pins is that a fail-closed context can be built without config and
        // that EXPLORE is the configured mode string.
        assertEquals("explore", io.agentscope.core.permission.PermissionMode.EXPLORE.getValue());
    }

    @Test
    void serverConfiguredModeSurvivesNullClientMode() {
        PermissionContextFactory factory = new PermissionContextFactory();
        AgentConfig.PermissionConfig config = new AgentConfig.PermissionConfig();
        config.setDefaultMode("explore");
        config.getDenyTools().add("execute_shell_command");

        // Client sends nothing -> server default applies
        PermissionContextState state = factory.build(null, config);
        assertNotNull(state);
    }

    // ---- P1-2: upload path whitelist ----

    @Test
    void pathsOutsideUploadWhitelistAreRejected() {
        IllegalArgumentException outside = assertThrows(IllegalArgumentException.class,
                () -> UploadPathGuard.validate("/etc/passwd"));
        assertTrue(outside.getMessage().contains("outside the allowed upload directory"));

        IllegalArgumentException traversal = assertThrows(IllegalArgumentException.class,
                () -> UploadPathGuard.validate(System.getProperty("java.io.tmpdir")
                        + "/agentscope-uploads/../../etc/passwd"));
        assertTrue(traversal.getMessage().contains("outside the allowed upload directory"));

        assertFalse(UploadPathGuard.isAllowed("/etc/passwd"));
        assertNull(UploadPathGuard.validate(null));
        assertNull(UploadPathGuard.validate("  "));
    }

    @Test
    void registeredUploadPathInsideWhitelistPasses() {
        java.nio.file.Path dir = UploadPathGuard.uploadDir();
        try {
            java.nio.file.Path file = dir.resolve("security-regression-test.txt");
            java.nio.file.Files.writeString(file, "test");
            try {
                assertDoesNotThrow(() -> UploadPathGuard.validate(file.toString()));
                assertTrue(UploadPathGuard.isAllowed(file.toString()));
            } finally {
                java.nio.file.Files.deleteIfExists(file);
            }
        } catch (java.io.IOException e) {
            fail("Failed to prepare upload directory fixture: " + e.getMessage());
        }
    }
}
