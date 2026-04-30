package com.skloda.agentscope.service;

import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.model.PendingApproval;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pending human-in-the-loop approval requests.
 * Caches paused agent instances between the pause and resume requests.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    private static final long EXPIRY_MINUTES = 5;

    private final ConcurrentHashMap<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

    public String registerPendingApproval(ReActAgent agent, ObservabilityHook hook,
                                           List<ToolUseBlock> toolCalls,
                                           String agentId, String sessionId) {
        String approvalId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        PendingApproval pa = PendingApproval.builder()
                .approvalId(approvalId)
                .agentId(agentId)
                .sessionId(sessionId)
                .agent(agent)
                .hook(hook)
                .pendingToolCalls(toolCalls)
                .createdAt(Instant.now())
                .build();
        pendingApprovals.put(approvalId, pa);
        log.info("Registered pending approval: {} for agent: {} ({} tool calls)",
                approvalId, agentId, toolCalls.size());
        return approvalId;
    }

    public PendingApproval getPendingApproval(String approvalId) {
        return pendingApprovals.get(approvalId);
    }

    public void removePendingApproval(String approvalId) {
        PendingApproval removed = pendingApprovals.remove(approvalId);
        if (removed != null) {
            log.info("Removed pending approval: {}", approvalId);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpired() {
        Instant cutoff = Instant.now().minus(EXPIRY_MINUTES, ChronoUnit.MINUTES);
        int removed = 0;
        var iterator = pendingApprovals.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().getCreatedAt().isBefore(cutoff)) {
                iterator.remove();
                removed++;
                log.info("Expired pending approval: {}", entry.getKey());
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired pending approvals", removed);
        }
    }
}
