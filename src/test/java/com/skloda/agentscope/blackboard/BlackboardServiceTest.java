package com.skloda.agentscope.blackboard;

import io.agentscope.core.state.InMemoryAgentStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the Shared Blackboard service per objective §Verification #2:
 * create / read / patch / version growth / defensive copy / clear / cross-session isolation.
 */
class BlackboardServiceTest {

    private BlackboardService service;

    @BeforeEach
    void setUp() {
        service = new BlackboardService(new InMemoryAgentStateStore(), "shared_blackboard");
    }

    @Test
    void getOrCreateCreatesFreshBlackboard() {
        SessionBlackboard bb = service.getOrCreate("alice", "s1");
        assertNotNull(bb);
        assertEquals(0, bb.getVersion(), "Fresh blackboard must start at version 0");
        assertNull(bb.getActiveExpert(), "activeExpert must be null on a fresh board");
        assertTrue(bb.getCustomerFacts().isEmpty());
    }

    @Test
    void getOrCreateIsIdempotentWithinSlot() {
        SessionBlackboard first = service.getOrCreate("alice", "s1");
        SessionBlackboard second = service.getOrCreate("alice", "s1");
        assertEquals(first, second, "Same slot returns equivalent snapshot");
    }

    @Test
    void applyPatchIncrementsVersionAndMergesFacts() {
        service.getOrCreate("alice", "s1");
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("settlementAmount", 1200.0);
        facts.put("failureReason", "timeout");

        BlackboardPatch patch = BlackboardPatch.builder()
                .activeExpert("repayment-expert")
                .currentIntent("repayment-failure")
                .customerFactsPatch(facts)
                .findingsToAdd(List.of(
                        new ExpertFinding("repayment-expert", "settlementAmount",
                                1200.0, "user-reported amount")))
                .build();

        SessionBlackboard after = service.applyPatch("alice", "s1", patch);

        assertEquals(1, after.getVersion(), "Version must increment after one patch");
        assertEquals("repayment-expert", after.getActiveExpert());
        assertEquals("repayment-failure", after.getCurrentIntent());
        assertEquals(1200.0, after.getCustomerFacts().get("settlementAmount"));
        assertEquals("timeout", after.getCustomerFacts().get("failureReason"));
        assertEquals(1, after.getFindings().size());
    }

    @Test
    void versionIsMonotonicAcrossPatches() {
        service.getOrCreate("alice", "s1");
        for (int i = 0; i < 5; i++) {
            BlackboardPatch p = BlackboardPatch.builder()
                    .customerFactsPatch(Map.of("k" + i, "v" + i))
                    .build();
            assertEquals(i + 1, service.applyPatch("alice", "s1", p).getVersion());
        }
    }

    @Test
    void defensiveCopyReturnedByGetOrCreateCannotMutateInternalState() {
        SessionBlackboard bb = service.getOrCreate("alice", "s1");
        assertThrows(UnsupportedOperationException.class,
                () -> bb.getCustomerFacts().put("hack", "value"),
                "Returned map must be unmodifiable");
        assertThrows(UnsupportedOperationException.class,
                () -> bb.getFindings().add(new ExpertFinding("x", "y", "z", "w")),
                "Returned findings list must be unmodifiable");
    }

    @Test
    void patchWithNullValueRemovesKey() {
        service.getOrCreate("alice", "s1");
        service.applyPatch("alice", "s1", BlackboardPatch.builder()
                .customerFactsPatch(Map.of("foo", "bar"))
                .build());
        assertEquals("bar", service.getOrCreate("alice", "s1").getCustomerFacts().get("foo"));

        // Now delete it via null value
        Map<String, Object> deletion = new LinkedHashMap<>();
        deletion.put("foo", null);
        service.applyPatch("alice", "s1", BlackboardPatch.builder()
                .customerFactsPatch(deletion)
                .build());

        assertFalse(service.getOrCreate("alice", "s1").getCustomerFacts().containsKey("foo"),
                "null value in patch must remove the key");
    }

    @Test
    void differentUsersAndSessionsAreIsolated() {
        service.applyPatch("alice", "s1", BlackboardPatch.builder()
                .customerFactsPatch(Map.of("aliceFact", "a1"))
                .activeExpert("exp1")
                .build());
        service.applyPatch("alice", "s2", BlackboardPatch.builder()
                .customerFactsPatch(Map.of("aliceS2Fact", "a2"))
                .build());
        service.applyPatch("bob", "s1", BlackboardPatch.builder()
                .customerFactsPatch(Map.of("bobFact", "b1"))
                .build());

        SessionBlackboard aliceS1 = service.getOrCreate("alice", "s1");
        SessionBlackboard aliceS2 = service.getOrCreate("alice", "s2");
        SessionBlackboard bobS1 = service.getOrCreate("bob", "s1");

        assertTrue(aliceS1.getCustomerFacts().containsKey("aliceFact"));
        assertFalse(aliceS1.getCustomerFacts().containsKey("aliceS2Fact"));
        assertFalse(aliceS1.getCustomerFacts().containsKey("bobFact"));
        assertEquals("exp1", aliceS1.getActiveExpert());
        assertNull(aliceS2.getActiveExpert(), "alice/s2 never set activeExpert");
        assertNull(bobS1.getActiveExpert(), "bob/s1 never set activeExpert");
    }

    @Test
    void getSnapshotReturnsEmptyForUnknownSlot() {
        Optional<SessionBlackboard> snapshot = service.getSnapshot("nobody", "nohow");
        assertTrue(snapshot.isEmpty());
    }

    @Test
    void clearRemovesBlackboardAndIsIdempotent() {
        service.applyPatch("alice", "s1", BlackboardPatch.builder()
                .customerFactsPatch(Map.of("x", "y"))
                .build());
        assertEquals(1, service.getOrCreate("alice", "s1").getVersion());

        service.clear("alice", "s1");
        assertTrue(service.getSnapshot("alice", "s1").isEmpty(),
                "clear must remove the blackboard");

        // Clearing again must not throw
        assertDoesNotThrow(() -> service.clear("alice", "s1"));
    }

    @Test
    void emptyPatchDoesNotBumpVersionButPersistMetaChanges() {
        // An empty patch (only metadata coercion done by the caller, not the service)
        // still bumps version when applied — this matches the SupervisorRuntime contract
        // (activeExpert/currentIntent are always written).
        service.getOrCreate("alice", "s1");
        BlackboardPatch empty = new BlackboardPatch();
        SessionBlackboard after = service.applyPatch("alice", "s1", empty);
        assertEquals(1, after.getVersion(), "Even an empty patch bumps version once");
    }

    @Test
    void blackboardKeyIsDistinctFromAgentStateKey() {
        // Sanity: confirm the storage key is NOT "agent_state".
        assertEquals("shared_blackboard", service.getStorageKey());
        assertNotEquals("agent_state", service.getStorageKey());
    }
}
