package com.skloda.agentscope.service;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.model.SessionInfo;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SessionManagerServiceTest {

    private SessionManagerService sessionManagerService;

    @Mock
    private AgentFactory mockAgentFactory;

    @Mock
    private AgentConfigService mockConfigService;

    @Mock
    private ReActAgent mockAgent;

    @Mock
    private Memory mockMemory;

    @Mock
    private AgentConfig mockAgentConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sessionManagerService = new SessionManagerService(
            mockAgentFactory, mockConfigService, "/tmp/test-sessions"
        );

        // Setup common mock behavior
        when(mockAgentFactory.createMemory(anyString())).thenReturn(mockMemory);
        when(mockAgentFactory.createAgentForSession(anyString(), any())).thenReturn(mockAgent);
        when(mockAgentConfig.getName()).thenReturn("Test Agent");
    }

    @Test
    void constructor_initializesService() {
        assertNotNull(sessionManagerService);
    }

    @Test
    void createNewSession_returnsNewSessionContext() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext context = sessionManagerService.createNewSession("agent-1");

        assertNotNull(context);
        assertNotNull(context.getSessionId());
        assertFalse(context.getSessionId().isEmpty());
        assertEquals("agent-1", context.getAgentId());
        assertSame(mockAgent, context.getAgent());
        assertSame(mockMemory, context.getMemory());
    }

    @Test
    void createNewSession_generatesUniqueSessionIds() {
        when(mockConfigService.findAgentConfig(anyString())).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext ctx1 = sessionManagerService.createNewSession("agent-1");
        SessionManagerService.SessionContext ctx2 = sessionManagerService.createNewSession("agent-1");

        assertNotEquals(ctx1.getSessionId(), ctx2.getSessionId());
    }

    @Test
    void getOrCreateSession_withNullSessionId_createsNewSession() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext context = sessionManagerService.getOrCreateSession(null, "agent-1");

        assertNotNull(context);
        assertEquals("agent-1", context.getAgentId());
    }

    @Test
    void getOrCreateSession_withEmptySessionId_createsNewSession() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext context = sessionManagerService.getOrCreateSession("", "agent-1");

        assertNotNull(context);
        assertEquals("agent-1", context.getAgentId());
    }

    @Test
    void getOrCreateSession_withValidSessionId_returnsExistingSession() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext original = sessionManagerService.createNewSession("agent-1");
        long originalLastAccessed = original.getLastAccessedAt();

        SessionManagerService.SessionContext retrieved = sessionManagerService.getOrCreateSession(
            original.getSessionId(), "agent-1"
        );

        assertSame(original, retrieved);
        assertTrue(retrieved.getLastAccessedAt() >= originalLastAccessed);
    }

    @Test
    void getOrCreateSessionForAgent_createsNewSessionIfNoneExists() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext context = sessionManagerService.getOrCreateSessionForAgent("agent-1");

        assertNotNull(context);
        assertEquals("agent-1", context.getAgentId());
    }

    @Test
    void getOrCreateSessionForAgent_returnsExistingSessionForAgent() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext original = sessionManagerService.createNewSession("agent-1");

        SessionManagerService.SessionContext retrieved = sessionManagerService.getOrCreateSessionForAgent("agent-1");

        assertSame(original, retrieved);
    }

    @Test
    void saveSession_updatesLastAccessedTime() throws InterruptedException {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext context = sessionManagerService.createNewSession("agent-1");
        long originalLastAccessed = context.getLastAccessedAt();

        Thread.sleep(10); // Small delay to ensure time difference
        sessionManagerService.saveSession(context.getSessionId());

        assertTrue(context.getLastAccessedAt() > originalLastAccessed);
    }

    @Test
    void saveSession_handlesNonExistentSession() {
        assertDoesNotThrow(() -> sessionManagerService.saveSession("non-existent"));
    }

    @Test
    void listSessions_returnsEmptyListInitially() {
        List<SessionInfo> sessions = sessionManagerService.listSessions();

        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void listSessions_returnsActiveSessions() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        sessionManagerService.createNewSession("agent-1");

        List<SessionInfo> sessions = sessionManagerService.listSessions();

        assertEquals(1, sessions.size());
        assertEquals("agent-1", sessions.get(0).getAgentId());
    }

    @Test
    void listSessions_sortsByLastAccessedDescending() throws InterruptedException {
        when(mockConfigService.findAgentConfig(anyString())).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext ctx1 = sessionManagerService.createNewSession("agent-1");
        Thread.sleep(10);
        SessionManagerService.SessionContext ctx2 = sessionManagerService.createNewSession("agent-2");

        List<SessionInfo> sessions = sessionManagerService.listSessions();

        assertEquals(2, sessions.size());
        assertEquals(ctx2.getSessionId(), sessions.get(0).getSessionId());
        assertEquals(ctx1.getSessionId(), sessions.get(1).getSessionId());
    }

    @Test
    void deleteSession_removesSession() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext context = sessionManagerService.createNewSession("agent-1");
        assertNotNull(sessionManagerService.getSession(context.getSessionId()));

        sessionManagerService.deleteSession(context.getSessionId());

        assertNull(sessionManagerService.getSession(context.getSessionId()));
    }

    @Test
    void deleteSession_handlesNonExistentSession() {
        assertDoesNotThrow(() -> sessionManagerService.deleteSession("non-existent"));
    }

    @Test
    void getSession_returnsSessionContext() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext original = sessionManagerService.createNewSession("agent-1");

        SessionManagerService.SessionContext retrieved = sessionManagerService.getSession(original.getSessionId());

        assertSame(original, retrieved);
    }

    @Test
    void getSession_withNullSessionId_returnsNull() {
        SessionManagerService.SessionContext result = sessionManagerService.getSession(null);
        assertNull(result);
    }

    @Test
    void getSession_withNonExistentSessionId_returnsNull() {
        SessionManagerService.SessionContext result = sessionManagerService.getSession("non-existent");
        assertNull(result);
    }

    @Test
    void listSessions_includesMessageCount() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        sessionManagerService.createNewSession("agent-1");

        List<SessionInfo> sessions = sessionManagerService.listSessions();

        assertEquals(1, sessions.size());
        // Message count is 0 for new sessions with empty memory
        assertEquals(0, sessions.get(0).getMessageCount());
    }

    @Test
    void contextTouch_updatesLastAccessedTime() throws InterruptedException {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext context = sessionManagerService.createNewSession("agent-1");
        long originalLastAccessed = context.getLastAccessedAt();

        Thread.sleep(10);
        context.touch();

        assertTrue(context.getLastAccessedAt() > originalLastAccessed);
    }

    @Test
    void sessionContextGetters_returnCorrectValues() {
        when(mockConfigService.findAgentConfig("agent-1")).thenReturn(Optional.of(mockAgentConfig));

        SessionManagerService.SessionContext context = sessionManagerService.createNewSession("agent-1");

        assertEquals("agent-1", context.getAgentId());
        assertSame(mockAgent, context.getAgent());
        assertSame(mockMemory, context.getMemory());
        assertNotNull(context.getSessionId());
        assertTrue(context.getLastAccessedAt() > 0);
    }
}
