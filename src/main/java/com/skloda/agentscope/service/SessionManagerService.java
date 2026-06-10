package com.skloda.agentscope.service;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.model.SessionInfo;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.session.Session;
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

@Service
public class SessionManagerService {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentFactory agentFactory;
    private final AgentConfigService configService;
    private final Path sessionBasePath;

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

    public static class SessionContext {
        private final String sessionId;
        private final String agentId;
        private final ReActAgent agent;
        private final Session session;
        private final long createdAt;
        private volatile long lastAccessedAt;

        SessionContext(String sessionId, String agentId, ReActAgent agent,
                       Session session) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.agent = agent;
            this.session = session;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessedAt = this.createdAt;
        }

        public String getSessionId() { return sessionId; }
        public String getAgentId() { return agentId; }
        public ReActAgent getAgent() { return agent; }
        public Session getSession() { return session; }

        public long getLastAccessedAt() { return lastAccessedAt; }
        public void touch() { this.lastAccessedAt = System.currentTimeMillis(); }
    }

    public SessionContext getOrCreateSession(String sessionId, String agentId) {
        if (sessionId != null && !sessionId.isBlank()) {
            SessionContext cached = activeSessions.get(sessionId);
            if (cached != null) {
                cached.touch();
                return cached;
            }
        }
        return createNewSession(agentId);
    }

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

    public SessionContext createNewSession(String agentId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return createSessionContext(sessionId, agentId);
    }

    private SessionContext createSessionContext(String sessionId, String agentId) {
        Session session = agentFactory.createSession();
        ReActAgent agent = agentFactory.createAgentForSession(agentId, session);

        SessionContext ctx = new SessionContext(sessionId, agentId, agent, session);
        activeSessions.put(sessionId, ctx);
        log.info("Created session: {} for agent: {}", sessionId, agentId);
        return ctx;
    }

    public void saveSession(String sessionId) {
        SessionContext ctx = activeSessions.get(sessionId);
        if (ctx == null) return;
        ctx.touch();
        log.debug("Session {} touched", sessionId);
    }

    public List<SessionInfo> listSessions() {
        List<Map.Entry<String, SessionContext>> sorted = activeSessions.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().getLastAccessedAt(), a.getValue().getLastAccessedAt()))
                .toList();

        List<SessionInfo> result = new ArrayList<>();
        for (Map.Entry<String, SessionContext> entry : sorted) {
            String sid = entry.getKey();
            SessionContext ctx = entry.getValue();
            SessionInfo info = new SessionInfo();
            info.setSessionId(sid);
            info.setAgentId(ctx.getAgentId());
            AgentConfig cfg = configService.findAgentConfig(ctx.getAgentId()).orElse(null);
            info.setAgentName(cfg != null ? cfg.getName() : ctx.getAgentId());
            info.setMessageCount(0); // Session-based: message count no longer directly accessible
            info.setLastAccessedAt(formatTime(ctx.getLastAccessedAt()));
            result.add(info);
        }
        return result;
    }

    public void deleteSession(String sessionId) {
        activeSessions.remove(sessionId);
        log.info("Session {} deleted", sessionId);
    }

    public SessionContext getSession(String sessionId) {
        return sessionId != null ? activeSessions.get(sessionId) : null;
    }

    private String formatTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(FMT);
    }
}
