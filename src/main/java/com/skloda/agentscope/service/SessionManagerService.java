package com.skloda.agentscope.service;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.model.SessionInfo;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-process session lifecycle for the demo.
 * Each session holds Memory for the life of the current application process.
 */
@Service
public class SessionManagerService {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentFactory agentFactory;
    private final AgentConfigService configService;
    private final Path sessionBasePath;

    /** sessionId → SessionContext */
    private final ConcurrentHashMap<String, SessionContext> activeSessions = new ConcurrentHashMap<>();

    public SessionManagerService(AgentFactory agentFactory,
                                 AgentConfigService configService,
                                 @Value("${agentscope.session.storage-path:${user.home}/.agentscope/demo-sessions}") String storagePath) {
        this.agentFactory = agentFactory;
        this.configService = configService;
        this.sessionBasePath = Paths.get(storagePath);
        log.info("Session storage mode: in-memory (configured path ignored: {})",
                sessionBasePath.toAbsolutePath());
    }

    // ---- Inner context class ----

    public static class SessionContext {
        private final String sessionId;
        private final String agentId;
        private final ReActAgent agent;
        private final Memory memory;
        private final SessionManager sessionManager;
        private final long createdAt;
        private volatile long lastAccessedAt;

        SessionContext(String sessionId, String agentId, ReActAgent agent,
                       Memory memory, SessionManager sessionManager) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.agent = agent;
            this.memory = memory;
            this.sessionManager = sessionManager;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessedAt = this.createdAt;
        }

        public String getSessionId() { return sessionId; }
        public String getAgentId() { return agentId; }
        public ReActAgent getAgent() { return agent; }
        public Memory getMemory() { return memory; }
        public SessionManager getSessionManager() { return sessionManager; }

        public long getLastAccessedAt() { return lastAccessedAt; }
        public void touch() { this.lastAccessedAt = System.currentTimeMillis(); }
    }

    // ---- Core operations ----

    /**
     * Get or create a session. If sessionId is null/blank, creates a new session.
     */
    public SessionContext getOrCreateSession(String sessionId, String agentId) {
        if (sessionId != null && !sessionId.isBlank()) {
            SessionContext cached = activeSessions.get(sessionId);
            if (cached != null) {
                cached.touch();
                return cached;
            }
        }
        // Create new session
        return createNewSession(agentId);
    }

    /**
     * Get the current in-memory session for an agent, or create one.
     */
    public SessionContext getOrCreateSessionForAgent(String agentId) {
        return activeSessions.values().stream()
                .filter(ctx -> Objects.equals(ctx.getAgentId(), agentId))
                .findFirst()
                .map(ctx -> {
                    ctx.touch();
                    return ctx;
                })
                .orElseGet(() -> createNewSession(agentId));
    }

    /**
     * Create a brand new session with fresh agent and memory.
     */
    public SessionContext createNewSession(String agentId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return createSessionContext(sessionId, agentId);
    }

    private SessionContext createSessionContext(String sessionId, String agentId) {
        // Create memory based on config
        Memory memory = agentFactory.createMemory(agentId);

        // Create agent with this memory (hooks are per-request, not set here)
        ReActAgent agent = agentFactory.createAgentForSession(agentId, memory);

        SessionContext ctx = new SessionContext(sessionId, agentId, agent, memory, null);
        activeSessions.put(sessionId, ctx);
        log.info("Created session: {} for agent: {}", sessionId, agentId);
        return ctx;
    }

    /**
     * Mark session as recently used. In-memory demo sessions are not written to disk.
     */
    public void saveSession(String sessionId) {
        SessionContext ctx = activeSessions.get(sessionId);
        if (ctx == null) return;
        ctx.touch();
        log.debug("Session {} touched", sessionId);
    }

    /**
     * List all active in-memory sessions.
     */
    public List<SessionInfo> listSessions() {
        Map<String, SessionInfo> sessions = new LinkedHashMap<>();

        activeSessions.forEach((sid, ctx) -> {
            SessionInfo info = sessions.getOrDefault(sid, new SessionInfo());
            info.setSessionId(sid);
            info.setAgentId(ctx.getAgentId());
            AgentConfig cfg = configService.findAgentConfig(ctx.getAgentId()).orElse(null);
            info.setAgentName(cfg != null ? cfg.getName() : ctx.getAgentId());
            int msgCount = 0;
            if (ctx.getMemory() instanceof InMemoryMemory imm) {
                msgCount = imm.getMessages().size();
            }
            info.setMessageCount(msgCount);
            info.setLastAccessedAt(formatTime(ctx.getLastAccessedAt()));
            sessions.put(sid, info);
        });

        // Sort by last accessed (newest first)
        List<SessionInfo> result = new ArrayList<>(sessions.values());
        result.sort((a, b) -> {
            String ta = a.getLastAccessedAt() != null ? a.getLastAccessedAt() : "";
            String tb = b.getLastAccessedAt() != null ? b.getLastAccessedAt() : "";
            return tb.compareTo(ta);
        });
        return result;
    }

    /**
     * Delete a session from the active in-memory cache.
     */
    public void deleteSession(String sessionId) {
        activeSessions.remove(sessionId);
        log.info("Session {} deleted", sessionId);
    }

    /**
     * Get active session context if available.
     */
    public SessionContext getSession(String sessionId) {
        return sessionId != null ? activeSessions.get(sessionId) : null;
    }

    // ---- Helpers ----

    private String formatTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(FMT);
    }
}
