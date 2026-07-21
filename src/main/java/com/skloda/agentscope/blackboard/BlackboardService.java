package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service façade for reading and writing {@link SessionBlackboard}s keyed by
 * {@code (userId, sessionId)}.
 *
 * <p><b>Storage boundary</b>: the blackboard is persisted under the
 * {@link com.skloda.agentscope.agent.AgentConfig.SharedBlackboardConfig#getStorageKey() configured key}
 * (default {@code "shared_blackboard"}) inside the supplied {@link AgentStateStore}. It is NEVER
 * written under {@code "agent_state"}, so it never collides with the Supervisor's Conversation
 * AgentState or with any expert's private state.
 *
 * <p><b>Concurrency</b>: all operations for a given {@code (userId, sessionId)} slot are
 * serialized by a per-slot {@link ReentrantLock}. Patches are applied serially, version is
 * monotonic per slot, and the last-writer-wins risk is avoided within a single slot.
 * Cross-slot operations run in parallel. This guarantee is in-process only — for
 * distributed {@code AgentStateStore} backends (Redis/MySQL/Postgres profiles) there is
 * currently no cross-process CAS; see the design doc for the atomic-Patch evolution path.
 *
 * <p><b>Write authority</b>: only the Supervisor runtime is expected to call
 * {@link #applyPatch}. Experts return {@link BlackboardPatch} payloads but never call
 * this method directly.
 */
@Service
public class BlackboardService {

    private static final Logger log = LoggerFactory.getLogger(BlackboardService.class);

    /** Default key if a config is not supplied (e.g. tests). Never "agent_state". */
    public static final String DEFAULT_STORAGE_KEY = "shared_blackboard";

    private final AgentStateStore backingStore;
    private final String storageKey;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Spring constructor. Uses an {@link InMemoryAgentStateStore} by default; production
     * deployments pass the distributed store bean (S13: Redis/MySQL/Postgres profile).
     */
    @Autowired
    public BlackboardService(
            @Autowired(required = false) AgentStateStore backingStore,
            AgentConfigService configService) {
        this.backingStore = backingStore != null ? backingStore : new InMemoryAgentStateStore();
        this.storageKey = resolveStorageKey(configService);
        log.info("BlackboardService initialized with store={} key='{}'",
                this.backingStore.getClass().getSimpleName(), this.storageKey);
    }

    /** Test constructor with explicit store and key. */
    public BlackboardService(AgentStateStore backingStore, String storageKey) {
        this.backingStore = Objects.requireNonNull(backingStore);
        this.storageKey = storageKey == null || storageKey.isBlank()
                ? DEFAULT_STORAGE_KEY : storageKey;
    }

    private static String resolveStorageKey(
            com.skloda.agentscope.agent.AgentConfigService configService) {
        // Look for any agent config that enables the blackboard and uses a custom key.
        // All supervisor agents in this project share the same blackboard key namespace
        // per (userId, sessionId), so the first enabled config wins.
        if (configService != null) {
            try {
                for (com.skloda.agentscope.agent.AgentConfig cfg : configService.getAllAgents()) {
                    AgentConfig.SharedBlackboardConfig bb = cfg.getSharedBlackboard();
                    if (bb != null && bb.isEnabled() && bb.getStorageKey() != null
                            && !bb.getStorageKey().isBlank()) {
                        return bb.getStorageKey();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to scan configs for blackboard key, using default: {}", e.getMessage());
            }
        }
        return DEFAULT_STORAGE_KEY;
    }

    /**
     * Get or create the blackboard for this slot, returning a deep defensive snapshot.
     */
    public SessionBlackboard getOrCreate(String userId, String sessionId) {
        return withLock(userId, sessionId, () -> {
            Optional<SessionBlackboard> existing = backingStore.get(
                    userId, sessionId, storageKey, SessionBlackboard.class);
            if (existing.isPresent()) {
                return existing.get().snapshot();
            }
            SessionBlackboard fresh = new SessionBlackboard();
            backingStore.save(userId, sessionId, storageKey, fresh);
            return fresh.snapshot();
        });
    }

    /** Get a snapshot if present, without creating one. */
    public Optional<SessionBlackboard> getSnapshot(String userId, String sessionId) {
        return withLock(userId, sessionId, () ->
                backingStore.get(userId, sessionId, storageKey, SessionBlackboard.class)
                        .map(SessionBlackboard::snapshot));
    }

    /**
     * Apply a validated patch as the sole writer. Increments version under the slot lock.
     *
     * @return the new snapshot (version bumped)
     */
    public SessionBlackboard applyPatch(String userId, String sessionId, BlackboardPatch patch) {
        Objects.requireNonNull(patch, "BlackboardPatch must not be null");
        return withLock(userId, sessionId, () -> {
            SessionBlackboard bb = backingStore
                    .get(userId, sessionId, storageKey, SessionBlackboard.class)
                    .orElseGet(SessionBlackboard::new);
            // We need a mutable copy to hand to applyPatch. Reconstruct via the public
            // constructor (SessionBlackboard already copies defensively on construction).
            SessionBlackboard mutable = reconstructForWrite(bb);
            mutable.applyPatch(patch);
            backingStore.save(userId, sessionId, storageKey, mutable);
            return mutable.snapshot();
        });
    }

    /** Remove the blackboard for this slot. Idempotent. */
    public void clear(String userId, String sessionId) {
        withLock(userId, sessionId, () -> {
            backingStore.delete(userId, sessionId, storageKey);
            return null;
        });
    }

    /** Visible for tests: how many distinct slots currently hold a blackboard. */
    public SetView listSlotsSnapshot() {
        return new SetView(backingStore.listSessionIds(null));
    }

    /** Simple read-only view to avoid leaking the store's live set reference. */
    public record SetView(java.util.Set<String> sessionIds) {
        public SetView {
            sessionIds = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(sessionIds));
        }
    }

    // ---- internals ----

    /**
     * The {@link AgentStateStore} SPI returns objects by reference for in-memory stores;
     * we must not mutate the stored instance directly (it would bypass our defensive-copy
     * invariant and could leak to concurrent readers). So we reconstruct a fresh
     * {@link SessionBlackboard} from the loaded data before calling {@code applyPatch}.
     */
    private SessionBlackboard reconstructForWrite(SessionBlackboard loaded) {
        return new SessionBlackboard(
                loaded.getVersion(),
                loaded.getActiveExpert(),
                loaded.getCurrentIntent(),
                loaded.getCustomerFacts(),
                loaded.getCollectedSlots(),
                loaded.getBusinessState(),
                loaded.getFindings(),
                loaded.getUnresolvedQuestions(),
                loaded.getCreatedAt(),
                loaded.getUpdatedAt());
    }

    private <T> T withLock(String userId, String sessionId, java.util.function.Supplier<T> body) {
        String slotKey = slotKey(userId, sessionId);
        ReentrantLock lock = locks.computeIfAbsent(slotKey, k -> new ReentrantLock());
        lock.lock();
        try {
            return body.get();
        } finally {
            lock.unlock();
        }
    }

    private static String slotKey(String userId, String sessionId) {
        return (userId == null ? "__anon__" : userId) + "::"
                + (sessionId == null ? "__nosession__" : sessionId);
    }

    /** Visible for tests — exposes the configured storage key. */
    public String getStorageKey() {
        return storageKey;
    }
}
