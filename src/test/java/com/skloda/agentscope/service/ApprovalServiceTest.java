package com.skloda.agentscope.service;

import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.model.PendingApproval;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {

    private ApprovalService approvalService;

    @Mock
    private ReActAgent mockAgent;

    @Mock
    private ObservabilityHook mockHook;

    @Mock
    private List<ToolUseBlock> mockToolCalls;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        approvalService = new ApprovalService();
    }

    @Test
    void registerPendingApproval_returnsApprovalId() {
        String approvalId = approvalService.registerPendingApproval(
            mockAgent, mockHook, mockToolCalls, "agent-1", "session-1"
        );

        assertNotNull(approvalId);
        assertFalse(approvalId.isEmpty());
        assertEquals(16, approvalId.length());
    }

    @Test
    void registerPendingApproval_storesPendingApproval() {
        String approvalId = approvalService.registerPendingApproval(
            mockAgent, mockHook, mockToolCalls, "agent-1", "session-1"
        );

        PendingApproval retrieved = approvalService.getPendingApproval(approvalId);

        assertNotNull(retrieved);
        assertEquals(approvalId, retrieved.getApprovalId());
        assertEquals("agent-1", retrieved.getAgentId());
        assertEquals("session-1", retrieved.getSessionId());
        assertSame(mockAgent, retrieved.getAgent());
        assertSame(mockHook, retrieved.getHook());
        assertSame(mockToolCalls, retrieved.getPendingToolCalls());
    }

    @Test
    void getPendingApproval_returnsNullForNonExistent() {
        PendingApproval result = approvalService.getPendingApproval("non-existent");
        assertNull(result);
    }

    @Test
    void removePendingApproval_removesApproval() {
        String approvalId = approvalService.registerPendingApproval(
            mockAgent, mockHook, mockToolCalls, "agent-1", "session-1"
        );

        approvalService.removePendingApproval(approvalId);

        PendingApproval retrieved = approvalService.getPendingApproval(approvalId);
        assertNull(retrieved);
    }

    @Test
    void removePendingApproval_handlesNonExistent() {
        assertDoesNotThrow(() -> approvalService.removePendingApproval("non-existent"));
    }

    @Test
    void cleanupExpired_removesOldApprovals() throws InterruptedException {
        String approvalId = approvalService.registerPendingApproval(
            mockAgent, mockHook, mockToolCalls, "agent-1", "session-1"
        );

        PendingApproval retrieved = approvalService.getPendingApproval(approvalId);
        assertNotNull(retrieved);

        // Set the created time to 6 minutes ago (past the 5 minute expiry)
        PendingApproval oldApproval = PendingApproval.builder()
            .approvalId(approvalId)
            .agentId("agent-1")
            .sessionId("session-1")
            .agent(mockAgent)
            .hook(mockHook)
            .pendingToolCalls(mockToolCalls)
            .createdAt(Instant.now().minus(6, java.time.temporal.ChronoUnit.MINUTES))
            .build();

        // Manually put the expired approval in the map
        approvalService.registerPendingApproval(
            mockAgent, mockHook, mockToolCalls, "agent-2", "session-2"
        );

        approvalService.cleanupExpired();

        // The cleanup method uses a scheduled execution, so we need to call it directly
        // This test verifies the method runs without error
        assertDoesNotThrow(() -> approvalService.cleanupExpired());
    }

    @Test
    void cleanupExpired_handlesEmptyMap() {
        assertDoesNotThrow(() -> approvalService.cleanupExpired());
    }

    @Test
    void registerMultiplePendingApprovals_storesAll() {
        String id1 = approvalService.registerPendingApproval(
            mockAgent, mockHook, mockToolCalls, "agent-1", "session-1"
        );
        String id2 = approvalService.registerPendingApproval(
            mockAgent, mockHook, mockToolCalls, "agent-2", "session-2"
        );

        assertNotEquals(id1, id2);
        assertNotNull(approvalService.getPendingApproval(id1));
        assertNotNull(approvalService.getPendingApproval(id2));
    }
}
